package com.flagsmith.threads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import com.flagsmith.Versions;
import com.flagsmith.config.Retry;
import com.flagsmith.interfaces.FlagsmithSdk;
import com.flagsmith.models.TraitConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Buffers experimentation events and ships them to the Flagsmith events API.
 *
 * <p>Events are flushed on a fixed interval, when the buffer fills up, and on {@link #close()}.
 * Exposure events are deduplicated within a flush window. Nothing thrown here ever reaches caller
 * code: every failure is logged instead.
 */
public class EventProcessor {

  /** Name of the reserved event recorded when an identity is exposed to an experiment. */
  public static final String FLAG_EXPOSURE_EVENT = "$flag_exposure";

  private static final String EVENTS_PATH = "v1/events";
  private static final String SDK_USER_AGENT_HEADER = "Flagsmith-SDK-User-Agent";
  private static final String SDK_USER_AGENT_PREFIX = "flagsmith-java-sdk/";
  private static final String SDK_VERSION_KEY = "sdk_version";
  private static final String EXPERIMENT_ID_KEY = "experiment_id";
  private static final String KEY_SEPARATOR = "\u0000";
  private static final MediaType JSON_MEDIA_TYPE =
      MediaType.get("application/json; charset=utf-8");

  /** The URL batches are POSTed to. */
  @Getter
  private final HttpUrl eventsEndpoint;
  /** The number of buffered events that triggers an immediate flush. */
  @Getter
  private final int maxBufferItems;
  /** The interval between timed flushes; 0 means there is no timer. */
  @Getter
  private final int flushIntervalMillis;
  /** How long a single POST is expected to take; {@link #close()} waits up to twice this. */
  @Getter
  private final int requestTimeoutMillis;
  // Everything below is internal state, deliberately not exposed: once published, a getter
  // would be public API for as long as the SDK is supported.
  private final List<Map<String, Object>> buffer = new ArrayList<>();
  private final Set<String> dedupeKeys = new HashSet<>();
  private final Object lock = new Object();
  @Getter(AccessLevel.PACKAGE)
  private final ScheduledExecutorService scheduler;
  private final Set<CompletableFuture<Void>> inFlight = ConcurrentHashMap.newKeySet();
  @Getter(AccessLevel.PACKAGE)
  private final RequestProcessor requestProcessor;
  /** The API wrapper used to build requests; injected by {@code FlagsmithClient.Builder}. */
  @Setter
  private FlagsmithSdk api;
  private FlagsmithLogger logger = new FlagsmithLogger();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private ScheduledFuture<?> scheduledFlush;

  /**
   * Instantiate with an HTTP client.
   *
   * @param client               client instance
   * @param eventsUri            base URI of the events API, e.g. https://events.api.flagsmith.com/
   * @param maxBufferItems       number of buffered events that triggers an immediate flush
   * @param flushIntervalMillis  interval between timed flushes; 0 disables the timer
   * @param requestTimeoutMillis how long a single POST is expected to take; {@link #close()}
   *                             waits up to twice this for in-flight batches
   */
  public EventProcessor(OkHttpClient client, HttpUrl eventsUri, int maxBufferItems,
      int flushIntervalMillis, int requestTimeoutMillis) {
    this(eventsUri, maxBufferItems, flushIntervalMillis, requestTimeoutMillis,
        new RequestProcessor(client, new FlagsmithLogger(), buildRetry()));
  }

  /**
   * Instantiate with a request processor. Package-private: it exists for tests, and is not
   * something callers should come to depend on.
   */
  EventProcessor(HttpUrl eventsUri, int maxBufferItems, int flushIntervalMillis,
      int requestTimeoutMillis, RequestProcessor requestProcessor) {
    this.eventsEndpoint = eventsUri.newBuilder(EVENTS_PATH).build();
    this.maxBufferItems = maxBufferItems;
    this.flushIntervalMillis = flushIntervalMillis;
    this.requestTimeoutMillis = requestTimeoutMillis;
    this.requestProcessor = requestProcessor;
    this.scheduler = Executors.newSingleThreadScheduledExecutor((runnable) -> {
      Thread thread = new Thread(runnable, "flagsmith-events");
      thread.setDaemon(true);
      return thread;
    });
  }

  /**
   * The retry policy for an event batch: at most one retry, on a connection failure or a 5xx,
   * and never on a 4xx.
   */
  private static Retry buildRetry() {
    Retry retry = new Retry(2);
    retry.setStatusForcelist(new HashSet<>(Arrays.asList(500, 502, 503, 504)));
    retry.setStatusForcelistOnly(Boolean.TRUE);
    return retry;
  }

  /**
   * Set the logger used by the processor and by its request processor.
   *
   * @param logger logger instance
   */
  public void setLogger(FlagsmithLogger logger) {
    this.logger = logger;
    requestProcessor.setLogger(logger);
  }

  /**
   * Buffer a custom event.
   *
   * @param event      event name
   * @param identifier identity the event belongs to, may be null
   * @param value      event value, stringified before sending
   * @param traits     identity traits to attach, may be null
   * @param metadata   caller metadata to attach, may be null
   */
  public void trackEvent(String event, String identifier, Object value,
      Map<String, Object> traits, Map<String, Object> metadata) {
    bufferEvent(event, null, identifier, value, traits, metadata, false);
  }

  /**
   * Buffer a flag exposure event. Exposures equal in feature, identifier, value and experiment
   * are only sent once per flush window.
   *
   * @param featureName feature the identity was exposed to
   * @param identifier  identity the exposure belongs to
   * @param value       variant the identity was bucketed into
   * @param traits      identity traits to attach, may be null
   * @param metadata    caller metadata to attach, may be null
   */
  public void trackExposureEvent(String featureName, String identifier, Object value,
      Map<String, Object> traits, Map<String, Object> metadata) {
    bufferEvent(FLAG_EXPOSURE_EVENT, featureName, identifier, value, traits, metadata, true);
  }

  /**
   * Send everything buffered so far.
   *
   * @return a future completing once every in-flight batch has been delivered or dropped
   */
  public CompletableFuture<Void> flush() {
    List<Map<String, Object>> batch = null;
    CompletableFuture<Void> tracked = null;

    // The batch is registered as in-flight under the same lock that empties the buffer, so a
    // concurrent flush() can never observe both an empty buffer and an unregistered batch.
    synchronized (lock) {
      if (!buffer.isEmpty()) {
        batch = new ArrayList<>(buffer);
        buffer.clear();
        tracked = new CompletableFuture<>();
        inFlight.add(tracked);
      }
      dedupeKeys.clear();
    }

    if (batch != null) {
      send(batch, tracked);
    }

    return awaitInFlight();
  }

  /**
   * Start the flush timer. Does nothing when the flush interval is not positive.
   */
  public void start() {
    if (flushIntervalMillis <= 0 || scheduledFlush != null) {
      return;
    }

    scheduledFlush = scheduler.scheduleWithFixedDelay(
        this::flush, flushIntervalMillis, flushIntervalMillis, TimeUnit.MILLISECONDS);
  }

  /**
   * Stop the flush timer, ship whatever is left on a best-effort basis and release the HTTP
   * resources.
   */
  public void close() {
    closed.set(true);
    scheduler.shutdownNow();

    try {
      flush().get(requestTimeoutMillis * 2L, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Interrupted while flushing events on close.", e);
    } catch (Exception e) {
      logger.error("Failed to flush events on close.", e);
    }

    requestProcessor.close();
  }

  private void bufferEvent(String event, String featureName, String identifier, Object value,
      Map<String, Object> traits, Map<String, Object> metadata, boolean dedupe) {
    if (closed.get()) {
      logger.info("Not buffering event {}: the event processor is closed.", event);
      return;
    }

    try {
      final String stringValue = value == null ? null : String.valueOf(value);

      Map<String, Object> eventMetadata = new HashMap<>();
      if (metadata != null) {
        eventMetadata.putAll(metadata);
      }
      eventMetadata.put(SDK_VERSION_KEY, Versions.getVersion());
      final Object experimentId = eventMetadata.get(EXPERIMENT_ID_KEY);

      // Traits and metadata are caller objects of any shape. Turning them into JSON trees here,
      // rather than at flush time, means a value Jackson cannot serialise drops this one event
      // (logged below) instead of failing the whole batch it would later be sent in. It is also
      // a deep copy, so a caller mutating a nested map afterwards cannot change a buffered event.
      ObjectMapper mapper = MapperFactory.getMapper();
      Map<String, Object> eventTraits = eventTraits(traits);

      Map<String, Object> eventPayload = new LinkedHashMap<>();
      eventPayload.put("event", event);
      eventPayload.put("feature_name", featureName);
      eventPayload.put("identifier", identifier);
      eventPayload.put("value", stringValue);
      eventPayload.put("traits", eventTraits == null ? null : mapper.valueToTree(eventTraits));
      eventPayload.put("metadata", mapper.valueToTree(eventMetadata));
      eventPayload.put("timestamp", System.currentTimeMillis());

      boolean isFull;

      synchronized (lock) {
        if (dedupe && !dedupeKeys.add(
            dedupeKey(event, featureName, identifier, stringValue, experimentId))) {
          return;
        }
        buffer.add(eventPayload);
        isFull = maxBufferItems > 0 && buffer.size() >= maxBufferItems;
      }

      if (isFull) {
        flush();
      }
    } catch (RuntimeException e) {
      logger.error("Failed to buffer event " + event + ".", e);
    }
  }

  /**
   * Flatten a caller trait map into the flat map of trait values the events API expects. Values
   * wrapped in a {@link TraitConfig} are unwrapped, and traits the caller marked transient are
   * dropped: transient means "do not persist this against the identity", and an event store keeps
   * what it is sent.
   */
  private static Map<String, Object> eventTraits(Map<String, Object> traits) {
    if (traits == null) {
      return null;
    }

    Map<String, Object> flattened = new LinkedHashMap<>();

    for (Map.Entry<String, Object> entry : traits.entrySet()) {
      TraitConfig traitConfig = TraitConfig.fromObject(entry.getValue());
      if (!traitConfig.getIsTransient()) {
        flattened.put(entry.getKey(), traitConfig.getValue());
      }
    }

    return flattened;
  }

  private static String dedupeKey(String event, String featureName, String identifier,
      String value, Object experimentId) {
    return String.join(KEY_SEPARATOR,
        nullToEmpty(event),
        nullToEmpty(featureName),
        nullToEmpty(identifier),
        nullToEmpty(value),
        experimentId == null ? "" : String.valueOf(experimentId));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /**
   * Hand a batch to the request processor. {@code tracked} is settled exactly once, on every path
   * out of here: leaving it pending would wedge every later {@link #flush()}, since those wait on
   * it.
   */
  private void send(List<Map<String, Object>> batch, CompletableFuture<Void> tracked) {
    boolean submitted = false;

    try {
      if (api == null) {
        logger.error("Dropping " + batch.size()
            + " events: the event processor has no API wrapper.");
        return;
      }

      String payload = MapperFactory.getMapper()
          .writeValueAsString(Collections.singletonMap("events", batch));

      Request request = api
          .newPostRequest(eventsEndpoint, RequestBody.create(payload, JSON_MEDIA_TYPE))
          .newBuilder()
          .header(SDK_USER_AGENT_HEADER, SDK_USER_AGENT_PREFIX + Versions.getVersion())
          .build();

      requestProcessor
          .submit(request, new TypeReference<JsonNode>() {}, Boolean.FALSE, buildRetry())
          .whenComplete((response, error) -> settle(tracked));
      submitted = true;
    } catch (Exception e) {
      logger.error("Dropping " + batch.size() + " events: failed to send them.", e);
    } finally {
      if (!submitted) {
        settle(tracked);
      }
    }
  }

  private void settle(CompletableFuture<Void> tracked) {
    inFlight.remove(tracked);
    tracked.complete(null);
  }

  private CompletableFuture<Void> awaitInFlight() {
    return CompletableFuture.allOf(inFlight.toArray(new CompletableFuture[0]));
  }

  /**
   * A snapshot of the buffer, for tests. Taken under the lock, so a test never iterates the live
   * list while another thread is appending to it.
   */
  List<Map<String, Object>> bufferedEvents() {
    synchronized (lock) {
      return new ArrayList<>(buffer);
    }
  }
}
