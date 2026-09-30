package com.flagsmith.threads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.util.RawValue;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import com.flagsmith.Versions;
import com.flagsmith.exceptions.FlagsmithRuntimeError;
import com.flagsmith.interfaces.FlagsmithSdk;
import com.flagsmith.models.TraitConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Buffers experimentation events and sends them to the Flagsmith events API, on a timer, when the
 * buffer fills, and on {@link #close()}. A batch is retried on a 408, 429, 502, 503, 504 or a
 * network error, and put back in the buffer for the next timed flush if it still fails; any other
 * error drops it, and a 401 or 403 stops the processor. Exposures are deduplicated until they are
 * delivered or dropped.
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
  private static final MediaType JSON_MEDIA_TYPE =
      MediaType.get("application/json; charset=utf-8");
  static final int MAX_IN_FLIGHT_BATCHES = 2;
  static final int MAX_ATTEMPTS = 3;
  static final Set<Integer> RETRYABLE_STATUSES = Set.of(408, 429, 502, 503, 504);
  static final long BACKOFF_BASE_MILLIS = 1_000L;
  static final long BACKOFF_CAP_MILLIS = 10_000L;
  static final long CLOSE_TIMEOUT_MILLIS = 25_000L;
  private static final long DROP_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

  @Getter
  private final HttpUrl eventsEndpoint;
  @Getter
  private final int maxBufferItems;
  @Getter
  private final int flushIntervalMillis;
  private final List<Map<String, Object>> buffer = new ArrayList<>();
  private final Set<List<String>> dedupeKeys = new HashSet<>();
  private final Map<Map<String, Object>, List<String>> dedupeKeyByEvent = new IdentityHashMap<>();
  private final Object lock = new Object();
  @Getter(AccessLevel.PACKAGE)
  private final ScheduledExecutorService scheduler;
  private final Set<CompletableFuture<Void>> inFlight = ConcurrentHashMap.newKeySet();
  private CompletableFuture<Void> nextBatch = new CompletableFuture<>();
  private boolean held = false;                   // guarded by lock
  private int droppedSinceLastReport = 0;         // guarded by lock
  private Long lastDropReportNanos = null;        // guarded by lock
  private final AtomicLong droppedEvents = new AtomicLong();
  @Getter(AccessLevel.PACKAGE)
  private final RequestProcessor requestProcessor;
  @Getter(AccessLevel.PACKAGE)
  @Setter(AccessLevel.PACKAGE)
  private long closeTimeoutMillis;
  @Setter(AccessLevel.PACKAGE)
  private long backoffBaseMillis = BACKOFF_BASE_MILLIS;
  @Setter
  private FlagsmithSdk api;
  private FlagsmithLogger logger = new FlagsmithLogger();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicBoolean claimed = new AtomicBoolean(false);
  private final AtomicBoolean stopped = new AtomicBoolean(false);
  private ScheduledFuture<?> scheduledFlush;

  /**
   * Create a processor that sends batches through {@code client}.
   *
   * @param client               HTTP client; its timeouts also bound {@link #close()}
   * @param eventsUri            base URI of the events API, e.g. https://events.api.flagsmith.com/
   * @param maxBufferItems       number of buffered events that triggers an immediate flush, and
   *                             the most the buffer holds; at least 1
   * @param flushIntervalMillis  interval between timed flushes; 0 disables the timer
   * @throws IllegalArgumentException when maxBufferItems is below 1 or flushIntervalMillis is
   *                                  negative
   */
  public EventProcessor(OkHttpClient client, HttpUrl eventsUri, int maxBufferItems,
      int flushIntervalMillis) {
    this(eventsUri, maxBufferItems, flushIntervalMillis,
        new RequestProcessor(withCallDeadline(client), new FlagsmithLogger()));
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
    this.closeTimeoutMillis = worstCaseBatchMillis(requestProcessor.getClient());
    this.scheduler = Executors.newSingleThreadScheduledExecutor((runnable) -> {
      Thread thread = new Thread(runnable, "flagsmith-events");
      thread.setDaemon(true);
      return thread;
    });
  }

  private static long worstCaseBatchMillis(OkHttpClient client) {
    long attemptMillis = attemptMillis(client);
    if (attemptMillis == 0) {
      return CLOSE_TIMEOUT_MILLIS;
    }
    long total = MAX_ATTEMPTS * attemptMillis;
    for (int retry = 1; retry < MAX_ATTEMPTS; retry++) {
      total += backoffCeilingMillis(BACKOFF_BASE_MILLIS, retry);
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

  static long backoffCeilingMillis(long baseMillis, int retry) {
    return Math.min(BACKOFF_CAP_MILLIS, baseMillis << Math.min(retry - 1, 20));
  }

  static long backoffMillis(long baseMillis, int retry) {
    return ThreadLocalRandom.current().nextLong(backoffCeilingMillis(baseMillis, retry) + 1);
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
   * The number of events dropped so far: when the buffer overflowed, on a non-retryable error,
   * when the events API rejected them, after a 401 or 403, and when a batch failed on close. It
   * never decreases.
   *
   * @return the dropped event count
   */
  public long getDroppedEventCount() {
    return droppedEvents.get();
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
   * are only sent once until the events API accepts or drops them.
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
   * Send everything buffered so far, including events kept after a failure.
   *
   * @return a future completing once every event buffered so far has been sent, dropped or put
   *     back in the buffer; it never completes exceptionally
   */
  public CompletableFuture<Void> flush() {
    synchronized (lock) {
      held = false;
    }
    return dispatch();
  }

  private CompletableFuture<Void> dispatch() {
    Batch batch = null;
    CompletableFuture<Void> waiting = null;

    synchronized (lock) {
      if (!buffer.isEmpty() && (closed.get() || canSend())) {
        batch = takeBatch();
      } else if (!buffer.isEmpty() && !held) {
        waiting = nextBatch;
      }
    }

    if (batch != null) {
      send(batch);
    }

    CompletableFuture<Void> sent = awaitInFlight();
    return waiting == null ? sent : CompletableFuture.allOf(sent, waiting);
  }

  private boolean canSend() {
    return !held && inFlight.size() < MAX_IN_FLIGHT_BATCHES;
  }

  private Batch takeBatch() {
    Batch batch = new Batch(new ArrayList<>(buffer), nextBatch);
    buffer.clear();
    nextBatch = new CompletableFuture<>();
    inFlight.add(batch.tracked);
    return batch;
  }

  /**
   * Start the flush timer. Does nothing when the flush interval is not positive, when the timer
   * is already running, or once the processor is closed or stopped.
   */
  public synchronized void start() {
    if (flushIntervalMillis <= 0 || scheduledFlush != null || stopped.get()) {
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
   * batches settle, for at most one batch's worst case under the client's timeouts and the
   * retries, or {@link #CLOSE_TIMEOUT_MILLIS} if a timeout is off. A batch that fails from here on
   * is dropped, not kept.
   */
  public void close() {
    synchronized (this) {
      closed.set(true);
      scheduler.shutdownNow();
    }

    try {
      flush().get(closeTimeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Interrupted while flushing events on close.", e);
    } catch (TimeoutException e) {
      logger.error("Stopped waiting for events to be delivered after " + closeTimeoutMillis
          + "ms on close.");
    } catch (Exception e) {
      logger.error("Failed to flush events on close.", e);
    }

    requestProcessor.close();
  }

  private void bufferEvent(String event, String featureName, String identifier, Object value,
      Map<String, Object> traits, Map<String, Object> metadata, boolean dedupe) {
    if (closed.get()) {
      logClosed(event);
      return;
    }
    if (stopped.get()) {
      droppedEvents.incrementAndGet();
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

      Batch full = null;
      int droppedToReport = 0;

      synchronized (lock) {
        if (closed.get()) {
          logClosed(event);
          return;
        }
        if (stopped.get()) {
          droppedEvents.incrementAndGet();
          return;
        }
        if (dedupe) {
          List<String> key = dedupeKey(event, featureName, identifier, stringValue, experimentId);
          if (!dedupeKeys.add(key)) {
            return;
          }
          dedupeKeyByEvent.put(eventPayload, key);
        }
        if (buffer.size() >= maxBufferItems) {
          if (canSend()) {
            full = takeBatch();
          } else {
            droppedToReport = drop(Collections.singletonList(buffer.remove(0)));
          }
        }
        buffer.add(eventPayload);
        if (full == null && canSend() && buffer.size() >= maxBufferItems) {
          full = takeBatch();
        }
      }

      reportDrops(droppedToReport, "the buffer is full");
      if (full != null) {
        send(full);
      }
    } catch (JsonProcessingException | RuntimeException e) {
      droppedEvents.incrementAndGet();
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

  private static List<String> dedupeKey(String event, String featureName, String identifier,
      String value, Object experimentId) {
    return Arrays.asList(event, featureName, identifier, value,
        experimentId == null ? null : String.valueOf(experimentId));
  }

  private void send(Batch full) {
    List<Map<String, Object>> batch = full.events;
    CompletableFuture<Void> tracked = full.tracked;
    boolean submitted = false;
    String reason = "sending them failed";

    try {
      if (api == null) {
        reason = "the event processor has no API wrapper";
        return;
      }

      String payload = MapperFactory.getMapper()
          .writeValueAsString(Collections.singletonMap("events", batch));

      RequestBody body = RequestBody.create(payload, JSON_MEDIA_TYPE);
      Request request = new Request.Builder()
          .url(eventsEndpoint)
          .post(body)
          .header(AUTH_HEADER, api.newPostRequest(eventsEndpoint, body).headers(AUTH_HEADER).get(0))
          .header(USER_AGENT_HEADER, SDK_USER_AGENT_PREFIX + Versions.getVersion())
          .header(SDK_USER_AGENT_HEADER, SDK_USER_AGENT_PREFIX + Versions.getVersion())
          .header(ACCEPT_HEADER, "application/json")
          .build();

      requestProcessor.execute(() -> deliver(request, batch, tracked));
      submitted = true;
    } catch (Exception e) {
      logger.error("Failed to send " + batch.size() + " events.", e);
    } finally {
      if (!submitted) {
        settle(tracked, batch, Outcome.DROPPED, reason);
      }
    }
  }

  private void deliver(Request request, List<Map<String, Object>> batch,
      CompletableFuture<Void> tracked) {
    Outcome outcome = Outcome.DROPPED;
    String reason = "sending them failed";

    try {
      for (int attempt = 1; ; attempt++) {
        Integer status = null;
        String body = null;
        try (Response response = requestProcessor.getClient().newCall(request).execute()) {
          status = response.code();
          ResponseBody responseBody = response.body();
          if (response.isSuccessful() && responseBody != null) {
            body = responseBody.string();
          }
        } catch (IOException e) {
          reason = "sending them failed: " + e;
        }

        if (status != null && status >= 200 && status < 300) {
          countRejections(body, batch.size());
          outcome = Outcome.DELIVERED;
          break;
        }
        if (status != null) {
          reason = "the events API answered " + status;
        }
        if (status != null && (status == 401 || status == 403)) {
          outcome = Outcome.UNAUTHORISED;
          break;
        }
        if (status != null && !RETRYABLE_STATUSES.contains(status)) {
          break;
        }
        if (attempt == MAX_ATTEMPTS || stopped.get()) {
          outcome = Outcome.RETRY_LATER;
          break;
        }
        Thread.sleep(backoffMillis(backoffBaseMillis, attempt));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      outcome = Outcome.RETRY_LATER;
    } catch (RuntimeException e) {
      logger.error("Failed to send " + batch.size() + " events.", e);
    } finally {
      settle(tracked, batch, outcome, reason);
    }
  }

  private void countRejections(String body, int batchSize) {
    JsonNode rejected;
    try {
      JsonNode response = body == null ? null : MapperFactory.getMapper().readTree(body);
      rejected = response == null ? null : response.get("rejected");
    } catch (JsonProcessingException e) {
      return;
    }
    if (rejected != null && rejected.isArray() && rejected.size() > 0) {
      droppedEvents.addAndGet(rejected.size());
      logger.error("The events API rejected " + rejected.size() + " of " + batchSize
          + " events, which are not resent. First rejection: " + rejected.get(0));
    }
  }

  private void settle(CompletableFuture<Void> tracked, List<Map<String, Object>> batch,
      Outcome outcome, String reason) {
    int droppedToReport = 0;
    boolean kept = false;
    boolean stopping = false;
    boolean waiting;
    CompletableFuture<Void> released = null;

    try {
      synchronized (lock) {
        inFlight.remove(tracked);
        switch (outcome) {
          case DELIVERED:
            release(batch);
            break;
          case RETRY_LATER:
            if (closed.get() || stopped.get()) {
              droppedToReport = drop(batch);
            } else {
              droppedToReport = requeue(batch);
              kept = true;
              held = true;
              released = nextBatch;
              nextBatch = new CompletableFuture<>();
            }
            break;
          case UNAUTHORISED:
            stopping = stopped.compareAndSet(false, true);
            droppedEvents.addAndGet(batch.size());
            if (stopping) {
              droppedEvents.addAndGet(buffer.size());
              buffer.clear();
              dedupeKeys.clear();
              dedupeKeyByEvent.clear();
              released = nextBatch;
              nextBatch = new CompletableFuture<>();
            }
            break;
          default:
            droppedToReport = drop(batch);
        }
        waiting = !held && !buffer.isEmpty();
      }

      if (stopping) {
        stopTimer();
        logger.error("The events API refused the environment key (" + reason
            + "); events are dropped until the client is re-created with a valid key.");
      }
      if (kept) {
        logger.error("Kept " + batch.size() + " events for the next flush: " + reason + ".");
      }
      reportDrops(droppedToReport, kept ? "the buffer is full" : reason);
    } finally {
      tracked.complete(null);
      if (released != null) {
        released.complete(null);
      }
    }

    if (waiting) {
      dispatch();
    }
  }

  private int requeue(List<Map<String, Object>> batch) {
    buffer.addAll(0, batch);
    int overflow = buffer.size() - maxBufferItems;
    if (overflow <= 0) {
      return 0;
    }
    List<Map<String, Object>> oldest = buffer.subList(0, overflow);
    int toReport = drop(new ArrayList<>(oldest));
    oldest.clear();
    return toReport;
  }

  private void release(List<Map<String, Object>> events) {
    for (Map<String, Object> event : events) {
      List<String> key = dedupeKeyByEvent.remove(event);
      if (key != null) {
        dedupeKeys.remove(key);
      }
    }
  }

  private int drop(List<Map<String, Object>> events) {
    release(events);
    droppedEvents.addAndGet(events.size());
    return recordDrop(events.size());
  }

  private synchronized void stopTimer() {
    scheduler.shutdownNow();
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

  private void reportDrops(int dropped, String reason) {
    if (dropped > 0 && !stopped.get()) {
      logger.error("Dropped " + dropped + " events, latest because " + reason + ". Further drops"
          + " are reported at most every " + TimeUnit.NANOSECONDS.toSeconds(DROP_LOG_INTERVAL_NANOS)
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

  private enum Outcome { DELIVERED, RETRY_LATER, DROPPED, UNAUTHORISED }

  private static final class Batch {

    private final List<Map<String, Object>> events;
    private final CompletableFuture<Void> tracked;

    private Batch(List<Map<String, Object>> events, CompletableFuture<Void> tracked) {
      this.events = events;
      this.tracked = tracked;
    }
  }
}
