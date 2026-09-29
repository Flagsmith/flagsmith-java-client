package com.flagsmith.threads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.util.RawValue;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import com.flagsmith.Versions;
import com.flagsmith.config.Retry;
import com.flagsmith.exceptions.FlagsmithRuntimeError;
import com.flagsmith.interfaces.FlagsmithSdk;
import com.flagsmith.models.TraitConfig;
import java.util.ArrayList;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Buffers experimentation events and sends them to the Flagsmith events API, on a timer, when the
 * buffer fills, and on {@link #close()}. Exposures are deduplicated within a flush window.
 */
public class EventProcessor {

  /** Name of the reserved event recorded when an identity is exposed to an experiment. */
  public static final String FLAG_EXPOSURE_EVENT = "$flag_exposure";

  private static final String EVENTS_PATH = "v1/events";
  private static final String AUTH_HEADER = "X-Environment-Key";
  private static final String USER_AGENT_HEADER = "User-Agent";
  private static final String ACCEPT_HEADER = "Accept";
  private static final String SDK_USER_AGENT_HEADER = "Flagsmith-SDK-User-Agent";
  private static final String SDK_USER_AGENT_PREFIX = "flagsmith-java-sdk/";
  private static final String SDK_VERSION_KEY = "sdk_version";
  private static final String EXPERIMENT_ID_KEY = "experiment_id";
  private static final String KEY_SEPARATOR = "\u0000";
  private static final MediaType JSON_MEDIA_TYPE =
      MediaType.get("application/json; charset=utf-8");
  static final int MAX_IN_FLIGHT_EVENTS = 10_000;
  static final long CLOSE_TIMEOUT_MILLIS = 25_000L;
  private static final long DROP_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

  @Getter
  private final HttpUrl eventsEndpoint;
  @Getter
  private final int maxBufferItems;
  @Getter
  private final int flushIntervalMillis;
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
  @Getter(AccessLevel.PACKAGE)
  @Setter(AccessLevel.PACKAGE)
  private long closeTimeoutMillis;
  @Setter
  private FlagsmithSdk api;
  private FlagsmithLogger logger = new FlagsmithLogger();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicBoolean claimed = new AtomicBoolean(false);
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
        new RequestProcessor(withCallDeadline(client), new FlagsmithLogger(), buildRetry()));
  }

  EventProcessor(HttpUrl eventsUri, int maxBufferItems, int flushIntervalMillis,
      RequestProcessor requestProcessor) {
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

  private static Retry buildRetry() {
    Retry retry = new Retry(2);
    retry.setStatusForcelist(
        IntStream.rangeClosed(500, 599).boxed().collect(Collectors.toSet()));
    retry.setStatusForcelistOnly(Boolean.TRUE);
    return retry;
  }

  private static long worstCaseBatchMillis(OkHttpClient client, Retry retry) {
    long attemptMillis = attemptMillis(client);
    if (attemptMillis == 0) {
      return CLOSE_TIMEOUT_MILLIS;
    }
    long total = retry.getTotal() * attemptMillis;
    for (int retried = 1; retried < retry.getTotal(); retried++) {
      total += (long) (Math.min(retry.getBackoffFactor() * 2 * retried, retry.getBackoffMax())
          * 1000);
    }
    return total;
  }

  private static long attemptMillis(OkHttpClient client) {
    if (client.callTimeoutMillis() > 0) {
      return client.callTimeoutMillis();
    }
    if (client.connectTimeoutMillis() > 0 && client.writeTimeoutMillis() > 0
        && client.readTimeoutMillis() > 0) {
      return (long) client.connectTimeoutMillis() + client.writeTimeoutMillis()
          + client.readTimeoutMillis();
    }
    return 0;
  }

  private static OkHttpClient withCallDeadline(OkHttpClient client) {
    if (client.callTimeoutMillis() > 0) {
      return client;
    }
    long attemptMillis = attemptMillis(client);
    long callTimeout = attemptMillis > 0 ? attemptMillis : CLOSE_TIMEOUT_MILLIS;
    return client.newBuilder().callTimeout(callTimeout, TimeUnit.MILLISECONDS).build();
  }

  /**
   * Reserve this processor for one client; called by {@code FlagsmithClient.Builder}.
   *
   * @throws FlagsmithRuntimeError when another client already holds it
   */
  public void claim() {
    if (!claimed.compareAndSet(false, true)) {
      throw new FlagsmithRuntimeError("This event processor already backs another client.");
    }
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
   * @return a future completing once every event buffered so far has been delivered or dropped
   */
  public CompletableFuture<Void> flush() {
    List<Map<String, Object>> batch = null;
    CompletableFuture<Void> tracked = null;
    int droppedToReport = 0;
    boolean carried;

    synchronized (lock) {
      int admitted = Math.min(buffer.size(), MAX_IN_FLIGHT_EVENTS - inFlightEvents);
      if (admitted > 0) {
        List<Map<String, Object>> head = buffer.subList(0, admitted);
        batch = new ArrayList<>(head);
        head.clear();
        tracked = new CompletableFuture<>();
        inFlight.add(tracked);
        inFlightEvents += admitted;
      }
      int excess = buffer.size() - maxBufferItems;
      if (excess > 0) {
        buffer.subList(0, excess).clear();
        droppedToReport = recordDrop(excess);
      }
      carried = !buffer.isEmpty();
      dedupeKeys.clear();
    }

    logDrops(droppedToReport);
    if (batch != null) {
      send(batch, tracked);
    }

    CompletableFuture<Void> sent = awaitInFlight();
    return carried ? sent.thenCompose((ignored) -> flush()) : sent;
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
      logger.error("Not starting the event processor: it has been closed.");
      return;
    }

    scheduledFlush = scheduler.scheduleWithFixedDelay(
        this::flush, flushIntervalMillis, flushIntervalMillis, TimeUnit.MILLISECONDS);
  }

  /**
   * Stop the timer, flush what is left and release HTTP resources. Blocks until in-flight
   * batches settle, for at most one batch's worst case under the client's timeouts, or
   * {@link #CLOSE_TIMEOUT_MILLIS} if a timeout is off, then abandons the batches left.
   */
  public void close() {
    synchronized (this) {
      closed.set(true);
      scheduler.shutdownNow();
    }

    boolean settled = false;
    try {
      flush().get(closeTimeoutMillis, TimeUnit.MILLISECONDS);
      settled = true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Interrupted while flushing events on close.", e);
    } catch (TimeoutException e) {
      logger.error("Stopped waiting for events to be delivered after " + closeTimeoutMillis
          + "ms on close; abandoning the batches still in flight.");
    } catch (Exception e) {
      logger.error("Failed to flush events on close.", e);
    }

    if (settled) {
      requestProcessor.close();
    } else {
      requestProcessor.closeNow();
    }
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

      Map<String, Object> eventPayload = new LinkedHashMap<>();
      eventPayload.put("event", event);
      eventPayload.put("feature_name", featureName);
      eventPayload.put("identifier", identifier);
      eventPayload.put("value", stringValue);
      eventPayload.put("traits", eventTraits == null ? null : toJson(eventTraits));
      eventPayload.put("metadata", toJson(eventMetadata));
      eventPayload.put("timestamp", System.currentTimeMillis());

      boolean isFull = false;
      int droppedToReport = 0;

      synchronized (lock) {
        if (closed.get()) {
          logClosed(event);
          return;
        }
        if (dedupe && !dedupeKeys.add(
            dedupeKey(event, featureName, identifier, stringValue, experimentId))) {
          return;
        }
        buffer.add(eventPayload);
        if (inFlightEvents < MAX_IN_FLIGHT_EVENTS) {
          isFull = buffer.size() >= maxBufferItems;
        } else if (buffer.size() > maxBufferItems) {
          buffer.remove(0);
          droppedToReport = recordDrop(1);
        }
      }

      logDrops(droppedToReport);
      if (isFull) {
        flush();
      }
    } catch (JsonProcessingException | RuntimeException e) {
      logger.error("Failed to buffer event " + event + ".", e);
    }
  }

  private static RawValue toJson(Object value) throws JsonProcessingException {
    return new RawValue(MapperFactory.getMapper().writeValueAsString(value));
  }

  private void logClosed(String event) {
    logger.info("Not buffering event {}: the event processor is closed.", event);
  }

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

    return flattened.isEmpty() ? null : flattened;
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

  private void send(List<Map<String, Object>> batch, CompletableFuture<Void> tracked) {
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

      RequestBody body = RequestBody.create(payload, JSON_MEDIA_TYPE);
      Request request = new Request.Builder()
          .url(eventsEndpoint)
          .post(body)
          .header(AUTH_HEADER, api.newPostRequest(eventsEndpoint, body).header(AUTH_HEADER))
          .header(USER_AGENT_HEADER, SDK_USER_AGENT_PREFIX + Versions.getVersion())
          .header(SDK_USER_AGENT_HEADER, SDK_USER_AGENT_PREFIX + Versions.getVersion())
          .header(ACCEPT_HEADER, "application/json")
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

  private void logRejections(JsonNode response, int batchSize) {
    JsonNode rejected = response == null ? null : response.get("rejected");
    if (rejected != null && rejected.isArray() && rejected.size() > 0) {
      logger.error("The events API rejected " + rejected.size() + " of " + batchSize
          + " events. First rejection: " + rejected.get(0));
    }
  }

  private void settle(CompletableFuture<Void> tracked, int batchSize) {
    boolean waiting;
    synchronized (lock) {
      if (inFlight.remove(tracked)) {
        inFlightEvents -= batchSize;
      }
      waiting = !buffer.isEmpty();
    }
    tracked.complete(null);
    if (waiting) {
      flush();
    }
  }

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

  private void logDrops(int dropped) {
    if (dropped > 0) {
      logger.error("Dropped the " + dropped + " oldest events: " + MAX_IN_FLIGHT_EVENTS
          + " events are in flight to the events API and the buffer is full. Further drops are"
          + " reported at most every " + TimeUnit.NANOSECONDS.toSeconds(DROP_LOG_INTERVAL_NANOS)
          + "s.");
    }
  }

  private CompletableFuture<Void> awaitInFlight() {
    return CompletableFuture.allOf(inFlight.toArray(new CompletableFuture[0]));
  }

  List<Map<String, Object>> bufferedEvents() {
    synchronized (lock) {
      return new ArrayList<>(buffer);
    }
  }
}
