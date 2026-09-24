package com.flagsmith.threads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.util.RawValue;
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
import java.util.concurrent.TimeoutException;
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
 * <p>Events are flushed on a timer, when the buffer fills, and on {@link #close()}. Exposures are
 * deduplicated within a flush window. Exceptions are logged, never thrown to the caller;
 * {@link Error}s are not caught.
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
  /**
   * Cap on events awaiting the events API. Past it a flush drops its batch rather than queue it,
   * so an outage cannot grow memory with the host's traffic. It counts events, not batches, so a
   * small buffer is not throttled on a healthy API. The true bound is this plus one buffer.
   */
  static final int MAX_IN_FLIGHT_EVENTS = 10_000;
  /** The close timeout when the HTTP client's timeouts do not bound a request. */
  static final long UNBOUNDED = -1L;
  /** The least time between two error lines reporting dropped events. */
  private static final long DROP_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

  /** The URL batches are POSTed to. */
  @Getter
  private final HttpUrl eventsEndpoint;
  /** The number of buffered events that triggers an immediate flush. */
  @Getter
  private final int maxBufferItems;
  /** The interval between timed flushes; 0 means there is no timer. */
  @Getter
  private final int flushIntervalMillis;
  // No public getters below: each one would become supported API.
  private final List<Map<String, Object>> buffer = new ArrayList<>();
  private final Set<String> dedupeKeys = new HashSet<>();
  private final Object lock = new Object();
  @Getter(AccessLevel.PACKAGE)
  private final ScheduledExecutorService scheduler;
  private final Set<CompletableFuture<Void>> inFlight = ConcurrentHashMap.newKeySet();
  private int inFlightEvents = 0;                 // guarded by lock
  private int droppedSinceLastReport = 0;         // guarded by lock
  private Long lastDropReportNanos = null;        // guarded by lock
  @Getter(AccessLevel.PACKAGE)
  private final RequestProcessor requestProcessor;
  /**
   * How long {@link #close()} waits for in-flight batches: the worst case of one batch under the
   * HTTP client's timeouts and the retry policy, or {@link #UNBOUNDED} when a timeout is off.
   */
  @Getter(AccessLevel.PACKAGE)
  private final long closeTimeoutMillis;
  /** The API wrapper used to build requests; injected by {@code FlagsmithClient.Builder}. */
  @Setter
  private FlagsmithSdk api;
  private FlagsmithLogger logger = new FlagsmithLogger();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private ScheduledFuture<?> scheduledFlush;

  /**
   * Create a processor that sends batches through {@code client}.
   *
   * @param client               HTTP client; its timeouts also bound {@link #close()}
   * @param eventsUri            base URI of the events API, e.g. https://events.api.flagsmith.com/
   * @param maxBufferItems       number of buffered events that triggers an immediate flush; at
   *                             least 1
   * @param flushIntervalMillis  interval between timed flushes; 0 disables the timer
   * @throws IllegalArgumentException when maxBufferItems is below 1 or flushIntervalMillis is
   *                                  negative
   */
  public EventProcessor(OkHttpClient client, HttpUrl eventsUri, int maxBufferItems,
      int flushIntervalMillis) {
    this(eventsUri, maxBufferItems, flushIntervalMillis,
        new RequestProcessor(client, new FlagsmithLogger(), buildRetry()));
  }

  /** For tests: sends through the given request processor. */
  EventProcessor(HttpUrl eventsUri, int maxBufferItems, int flushIntervalMillis,
      RequestProcessor requestProcessor) {
    // The buffer limit is the only bound on the buffer between timed flushes, or with no timer.
    if (maxBufferItems < 1) {
      throw new IllegalArgumentException("maxBufferItems must be at least 1.");
    }
    if (flushIntervalMillis < 0) {
      throw new IllegalArgumentException("flushIntervalMillis must not be negative.");
    }
    this.eventsEndpoint = eventsUri.newBuilder(EVENTS_PATH).build();
    this.maxBufferItems = maxBufferItems;
    this.flushIntervalMillis = flushIntervalMillis;
    this.requestProcessor = requestProcessor;
    this.closeTimeoutMillis = worstCaseBatchMillis(requestProcessor.getClient(), buildRetry());
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
   * The longest one batch can take: every attempt the retry policy allows, each at the client's
   * timeouts, plus backoff. An attempt is bounded by the call timeout if set, else by connect,
   * write and read in turn.
   *
   * @return the worst case in milliseconds, or {@link #UNBOUNDED}
   */
  static long worstCaseBatchMillis(OkHttpClient client, Retry retry) {
    long attemptMillis;
    if (client.callTimeoutMillis() > 0) {
      attemptMillis = client.callTimeoutMillis();
    } else if (client.connectTimeoutMillis() > 0 && client.writeTimeoutMillis() > 0
        && client.readTimeoutMillis() > 0) {
      attemptMillis = (long) client.connectTimeoutMillis() + client.writeTimeoutMillis()
          + client.readTimeoutMillis();
    } else {
      return UNBOUNDED;
    }

    // Walk a copy of the policy the way RequestProcessor does: back off, then attempt.
    Retry walk = retry.toBuilder().build();
    long total = 0;
    for (int attempt = 0; attempt < walk.getTotal(); attempt++) {
      total += walk.calculateSleepTime() + attemptMillis;
      walk.retryAttempted();
    }
    return total;
  }

  /**
   * Set the logger, for this processor and its request processor.
   *
   * @param logger the client's logger, so event failures appear alongside its other output
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
    int droppedToReport = 0;

    // The batch is registered as in-flight under the same lock that empties the buffer, so a
    // concurrent flush() can never observe both an empty buffer and an unregistered batch.
    synchronized (lock) {
      if (!buffer.isEmpty()) {
        if (inFlightEvents >= MAX_IN_FLIGHT_EVENTS) {
          droppedToReport = recordDrop(buffer.size());
        } else {
          batch = new ArrayList<>(buffer);
          tracked = new CompletableFuture<>();
          inFlight.add(tracked);
          inFlightEvents += batch.size();
        }
        buffer.clear();
      }
      dedupeKeys.clear();
    }

    if (droppedToReport > 0) {
      logger.error("Dropped " + droppedToReport + " events: at least " + MAX_IN_FLIGHT_EVENTS
          + " earlier events are still waiting on the events API. Further drops are reported at"
          + " most every " + TimeUnit.NANOSECONDS.toSeconds(DROP_LOG_INTERVAL_NANOS) + "s.");
    }

    if (batch != null) {
      send(batch, tracked);
    }

    return awaitInFlight();
  }

  /**
   * Start the flush timer. Does nothing when the flush interval is not positive, when the timer
   * is already running, or once the processor is closed.
   */
  public synchronized void start() {
    if (flushIntervalMillis <= 0 || scheduledFlush != null) {
      return;
    }

    if (closed.get()) {
      // Scheduling on the shut-down scheduler would throw. Reached when a FlagsmithConfig is
      // reused after a client built from it was closed.
      logger.error("Not starting the event processor: it has been closed.");
      return;
    }

    scheduledFlush = scheduler.scheduleWithFixedDelay(
        this::flush, flushIntervalMillis, flushIntervalMillis, TimeUnit.MILLISECONDS);
  }

  /**
   * Stop the timer, flush what is left and release HTTP resources. Blocks until in-flight
   * batches settle, for at most the worst case of one batch; unbounded if a client timeout is off.
   */
  public void close() {
    synchronized (this) {
      closed.set(true);
      scheduler.shutdownNow();
    }

    try {
      CompletableFuture<Void> remaining = flush();
      if (closeTimeoutMillis == UNBOUNDED) {
        remaining.get();
      } else {
        remaining.get(closeTimeoutMillis, TimeUnit.MILLISECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Interrupted while flushing events on close.", e);
    } catch (TimeoutException e) {
      logger.error("Stopped waiting for events to be delivered after " + closeTimeoutMillis
          + "ms on close; batches still in flight carry on in the background.");
    } catch (Exception e) {
      logger.error("Failed to flush events on close.", e);
    }

    // A graceful shutdown: interrupting a POST mid-flight would lose its batch.
    requestProcessor.close();
  }

  private void bufferEvent(String event, String featureName, String identifier, Object value,
      Map<String, Object> traits, Map<String, Object> metadata, boolean dedupe) {
    if (closed.get()) {
      logClosed(event);
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

      Map<String, Object> eventTraits = eventTraits(traits);

      // Serialised now, not at flush: an unserialisable value drops only this event, not its
      // batch, and the JSON text is a deep copy that later caller mutation cannot reach.
      Map<String, Object> eventPayload = new LinkedHashMap<>();
      eventPayload.put("event", event);
      eventPayload.put("feature_name", featureName);
      eventPayload.put("identifier", identifier);
      eventPayload.put("value", stringValue);
      eventPayload.put("traits", eventTraits == null ? null : toJson(eventTraits));
      eventPayload.put("metadata", toJson(eventMetadata));
      eventPayload.put("timestamp", System.currentTimeMillis());

      boolean isFull;

      synchronized (lock) {
        // Re-checked under the lock: close() sets the flag before its final flush takes the
        // lock, so an event either makes that flush or is refused here, never stranded.
        if (closed.get()) {
          logClosed(event);
          return;
        }
        if (dedupe && !dedupeKeys.add(
            dedupeKey(event, featureName, identifier, stringValue, experimentId))) {
          return;
        }
        buffer.add(eventPayload);
        isFull = buffer.size() >= maxBufferItems;
      }

      if (isFull) {
        flush();
      }
    } catch (JsonProcessingException | RuntimeException e) {
      logger.error("Failed to buffer event " + event + ".", e);
    }
  }

  /**
   * JSON text written verbatim into the batch. Uses writeValueAsString, not valueToTree: on a
   * self-containing map valueToTree throws a raw StackOverflowError instead of an exception.
   */
  private static RawValue toJson(Object value) throws JsonProcessingException {
    return new RawValue(MapperFactory.getMapper().writeValueAsString(value));
  }

  private void logClosed(String event) {
    logger.info("Not buffering event {}: the event processor is closed.", event);
  }

  /**
   * Unwrap {@link TraitConfig} values and drop transient traits: transient means "do not
   * persist", and an event store keeps what it is sent.
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
   * Hand a batch to the request processor. {@code tracked} is settled on every path out: left
   * pending, it would wedge every later {@link #flush()}.
   */
  private void send(List<Map<String, Object>> batch, CompletableFuture<Void> tracked) {
    // The callback lives as long as the POST, so it holds the size and lets the list be freed.
    final int batchSize = batch.size();
    boolean submitted = false;

    try {
      if (api == null) {
        logger.error("Dropping " + batchSize
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
          .whenComplete((response, error) -> {
            try {
              logRejections(response, batchSize);
            } finally {
              settle(tracked, batchSize);
            }
          });
      submitted = true;
    } catch (Exception e) {
      logger.error("Dropping " + batchSize + " events: failed to send them.", e);
    } finally {
      if (!submitted) {
        settle(tracked, batchSize);
      }
    }
  }

  /**
   * The events API accepts a batch with a 202 even when it rejects some of its events, listing
   * those under {@code rejected}. Without this they would vanish without a trace.
   */
  private void logRejections(JsonNode response, int batchSize) {
    JsonNode rejected = response == null ? null : response.get("rejected");
    if (rejected != null && rejected.isArray() && rejected.size() > 0) {
      logger.error("The events API rejected " + rejected.size() + " of " + batchSize
          + " events. First rejection: " + rejected.get(0));
    }
  }

  private void settle(CompletableFuture<Void> tracked, int batchSize) {
    // Idempotent: only the call that actually removes the batch gives its events back.
    if (inFlight.remove(tracked)) {
      synchronized (lock) {
        inFlightEvents -= batchSize;
      }
    }
    tracked.complete(null);
  }

  /**
   * Count dropped events. The first drop is reported at once, later ones at most once per
   * {@link #DROP_LOG_INTERVAL_NANOS}, so an outage does not log per flush. Called under the lock.
   *
   * @return the number of drops to report now, or 0 to stay quiet
   */
  private int recordDrop(int count) {
    droppedSinceLastReport += count;
    long now = System.nanoTime();
    if (lastDropReportNanos != null && now - lastDropReportNanos < DROP_LOG_INTERVAL_NANOS) {
      return 0;
    }
    lastDropReportNanos = now;
    int toReport = droppedSinceLastReport;
    droppedSinceLastReport = 0;
    return toReport;
  }

  private CompletableFuture<Void> awaitInFlight() {
    return CompletableFuture.allOf(inFlight.toArray(new CompletableFuture[0]));
  }

  /** A snapshot of the buffer for tests, taken under the lock. */
  List<Map<String, Object>> bufferedEvents() {
    synchronized (lock) {
      return new ArrayList<>(buffer);
    }
  }
}
