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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongUnaryOperator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Buffers experimentation events and sends them to the Flagsmith events API, on a timer, when the
 * buffer fills, and on {@link #close()}. Exposures are deduplicated within a flush window.
 *
 * <p>One batch is in flight at a time. Each request times out after
 * {@link #REQUEST_TIMEOUT_MILLIS}, and a batch gets {@link #MAX_ATTEMPTS} attempts, backing off
 * from {@link #INITIAL_BACKOFF_MILLIS}, doubling, with full jitter. Transient failures (network
 * errors, timeouts, 408, 429, 500, 502, 503 and 504) are retried; a batch whose last attempt still
 * failed transiently is kept, ahead of newer events, for the next flush, unless that attempt got a
 * 500. Any other error response drops the batch, and a 401 or 403 stops event tracking altogether.
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
  static final int MAX_BUFFERED_EVENTS = 1_000;
  static final int MAX_ATTEMPTS = 3;
  static final long REQUEST_TIMEOUT_MILLIS = 3_000L;
  static final long INITIAL_BACKOFF_MILLIS = 1_000L;
  /** Every attempt of one batch timing out, with the longest backoff before each retry. */
  static final long CLOSE_TIMEOUT_MILLIS = MAX_ATTEMPTS * REQUEST_TIMEOUT_MILLIS
      + INITIAL_BACKOFF_MILLIS * ((1L << (MAX_ATTEMPTS - 1)) - 1);
  private static final long DROP_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);
  /**
   * With the flush timer off, how long a full buffer waits after a kept batch before it is sent
   * again: the default flush interval.
   */
  static final long HOLD_COOL_DOWN_MILLIS = 10_000L;

  /** How a batch's delivery ended. */
  enum Outcome {
    /** The events API accepted it. */
    SENT,
    /** Its last attempt failed transiently: buffer it again for the next flush. */
    KEPT,
    /** It is lost. */
    DROPPED,
    /** The events API refused the environment key: stop tracking events. */
    UNAUTHORISED
  }

  /** How one attempt to send a batch ended. */
  private enum Attempt {
    ACCEPTED,
    /** A network error, a timeout or a status worth retrying. */
    UNAVAILABLE,
    /** A 500: retried, but the batch is dropped if the last attempt gets one. */
    SERVER_ERROR,
    REJECTED,
    UNAUTHORISED
  }

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
  @Getter(AccessLevel.PACKAGE)
  private final ExecutorService sender;
  @Getter(AccessLevel.PACKAGE)
  private final OkHttpClient client;
  private final LongUnaryOperator jitter;
  private CompletableFuture<Void> inFlight = null;  // guarded by lock
  private CompletableFuture<Void> nextBatch = null; // guarded by lock
  // Whether a timer tick, the application or close() asked for the next batch, rather than
  // only a full buffer.
  private boolean nextBatchRequested = false;      // guarded by lock
  // After a kept batch, a full buffer waits for the next tick or explicit flush to send it, or,
  // with the timer off, for the cool-down to pass. Null when nothing holds it.
  private Long fullBufferHeldSinceNanos = null;     // guarded by lock
  private int droppedSinceLastReport = 0;           // guarded by lock
  private Long lastDropReportNanos = null;          // guarded by lock
  private volatile Call currentCall;
  private volatile boolean cancelled = false;
  @Getter(AccessLevel.PACKAGE)
  @Setter(AccessLevel.PACKAGE)
  private long closeTimeoutMillis = CLOSE_TIMEOUT_MILLIS;
  @Setter(AccessLevel.PACKAGE)
  private long holdCoolDownMillis = HOLD_COOL_DOWN_MILLIS;
  @Setter
  private FlagsmithSdk api;
  private FlagsmithLogger logger = new FlagsmithLogger();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicBoolean disabled = new AtomicBoolean(false);
  private final AtomicBoolean claimed = new AtomicBoolean(false);
  private ScheduledFuture<?> scheduledFlush;

  /**
   * Create a processor that sends batches through {@code client}.
   *
   * @param client               HTTP client; its interceptors apply, but each events request
   *                             times out after {@link #REQUEST_TIMEOUT_MILLIS}
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
        client.newBuilder()
            .callTimeout(REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            // Every attempt is one request, retried below with a backoff.
            .retryOnConnectionFailure(false)
            .build(),
        EventProcessor::fullJitter);
  }

  EventProcessor(HttpUrl eventsUri, int maxBufferItems, int flushIntervalMillis,
      OkHttpClient client, LongUnaryOperator jitter) {
    if (maxBufferItems < 1) {
      throw new IllegalArgumentException("maxBufferItems must be at least 1.");
    }
    if (flushIntervalMillis < 0) {
      throw new IllegalArgumentException("flushIntervalMillis must not be negative.");
    }
    this.eventsEndpoint = eventsUri.newBuilder(EVENTS_PATH).build();
    this.maxBufferItems = maxBufferItems;
    this.flushIntervalMillis = flushIntervalMillis;
    this.client = client;
    this.jitter = jitter;
    this.scheduler = Executors.newSingleThreadScheduledExecutor((runnable) -> {
      Thread thread = new Thread(runnable, "flagsmith-events");
      thread.setDaemon(true);
      return thread;
    });
    this.sender = Executors.newSingleThreadExecutor((runnable) -> {
      Thread thread = new Thread(runnable, "flagsmith-events-sender");
      thread.setDaemon(true);
      return thread;
    });
  }

  /** A random wait from none up to {@code backoffMillis}. */
  static long fullJitter(long backoffMillis) {
    return ThreadLocalRandom.current().nextLong(backoffMillis + 1);
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
   * Set the logger.
   *
   * @param logger the client's logger, so event failures appear alongside its other output
   */
  public void setLogger(FlagsmithLogger logger) {
    this.logger = logger;
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
   * Send everything buffered so far, a batch kept from a failed flush first. While a batch is in
   * flight, the buffer is sent once it settles, together with that batch if it is kept.
   *
   * @return a future completing, never exceptionally, once every event buffered so far has been
   *     sent, dropped or kept for the next flush, retries included
   */
  public CompletableFuture<Void> flush() {
    return flush(true);
  }

  /**
   * Send the buffer.
   *
   * @param requested false when only a full buffer asks for it: then, while the events API is
   *                  failing (the last batch was kept), the buffer waits for the next flush, or,
   *                  with the flush timer off, for {@link #HOLD_COOL_DOWN_MILLIS} to pass
   */
  private CompletableFuture<Void> flush(boolean requested) {
    List<Map<String, Object>> batch;
    CompletableFuture<Void> tracked;

    synchronized (lock) {
      if (disabled.get()) {
        clearBuffer();
        return CompletableFuture.completedFuture(null);
      }
      if (inFlight != null) {
        if (nextBatch == null && !buffer.isEmpty()) {
          nextBatch = new CompletableFuture<>();
        }
        nextBatchRequested |= requested && nextBatch != null;
        return nextBatch == null ? inFlight : nextBatch;
      }
      if (buffer.isEmpty() || (!requested && isFullBufferHeld())) {
        return CompletableFuture.completedFuture(null);
      }
      batch = takeBuffer();
      tracked = new CompletableFuture<>();
      inFlight = tracked;
    }

    send(batch, tracked);
    return tracked;
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
   * Stop the timer and send what is left, waiting for at most one batch's worst case:
   * {@link #CLOSE_TIMEOUT_MILLIS}.
   */
  public void close() {
    close(closeTimeoutMillis);
  }

  /**
   * Stop the timer and send what is left, retries included, for at most {@code timeoutMillis}.
   * Then abandon the request in flight and drop the events still unsent. Never throws.
   *
   * @param timeoutMillis how long to wait for the events to be sent
   */
  public void close(long timeoutMillis) {
    synchronized (this) {
      closed.set(true);
      scheduler.shutdownNow();
    }

    try {
      flush().get(Math.max(timeoutMillis, 0), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Interrupted while flushing events on close.", e);
    } catch (TimeoutException e) {
      logger.error("Stopped waiting for events to be delivered after " + timeoutMillis
          + "ms on close.");
    } catch (Exception e) {
      logger.error("Failed to flush events on close.", e);
    }

    cancelled = true;
    sender.shutdownNow();
    Call call = currentCall;
    if (call != null) {
      call.cancel();
    }

    int unsent;
    synchronized (lock) {
      unsent = buffer.size();
      clearBuffer();
    }
    if (unsent > 0) {
      logger.error("Dropped " + unsent + " events left unsent on close.");
    }
  }

  private void bufferEvent(String event, String featureName, String identifier, Object value,
      Map<String, Object> traits, Map<String, Object> metadata, boolean dedupe) {
    if (closed.get()) {
      logClosed(event);
      return;
    }
    if (disabled.get()) {
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

      boolean isFull;
      int droppedToReport;

      synchronized (lock) {
        if (closed.get()) {
          logClosed(event);
          return;
        }
        if (disabled.get()) {
          return;
        }
        if (dedupe) {
          List<String> key = dedupeKey(event, featureName, identifier, stringValue, experimentId);
          if (!dedupeKeys.add(key)) {
            return;
          }
          dedupeKeyByEvent.put(eventPayload, key);
        }
        buffer.add(eventPayload);
        droppedToReport = dropOverflow();
        isFull = buffer.size() >= maxBufferItems;
      }

      logDrops(droppedToReport);
      if (isFull) {
        flush(false);
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

  private static List<String> dedupeKey(String event, String featureName, String identifier,
      String value, Object experimentId) {
    return Arrays.asList(event, featureName, identifier, value,
        experimentId == null ? null : String.valueOf(experimentId));
  }

  /** Empties the buffer into a batch, opening a new dedupe window. Call holding the lock. */
  private List<Map<String, Object>> takeBuffer() {
    List<Map<String, Object>> batch = new ArrayList<>(buffer);
    clearBuffer();
    return batch;
  }

  /** Call holding the lock. */
  private void clearBuffer() {
    buffer.clear();
    dedupeKeys.clear();
    dedupeKeyByEvent.clear();
  }

  /**
   * Drops the oldest events beyond the buffer's capacity. Call holding the lock.
   *
   * @return the number of dropped events to log now
   */
  private int dropOverflow() {
    int overflow = buffer.size() - Math.max(maxBufferItems, MAX_BUFFERED_EVENTS);
    if (overflow <= 0) {
      return 0;
    }
    List<Map<String, Object>> oldest = buffer.subList(0, overflow);
    for (Map<String, Object> event : oldest) {
      dedupeKeys.remove(dedupeKeyByEvent.remove(event));
    }
    oldest.clear();
    return recordDrop(overflow);
  }

  private void send(List<Map<String, Object>> batch, CompletableFuture<Void> tracked) {
    try {
      Request request = buildRequest(batch);
      sender.execute(() -> {
        Outcome delivered = Outcome.DROPPED;
        try {
          delivered = deliver(request, batch.size());
        } catch (RuntimeException e) {
          logger.error("Dropping " + batch.size() + " events: failed to send them.", e);
        } finally {
          settle(batch, delivered, tracked);
        }
      });
      return;
    } catch (Exception e) {
      logger.error("Dropping " + batch.size() + " events: failed to send them.", e);
    }
    settle(batch, Outcome.DROPPED, tracked);
  }

  private Request buildRequest(List<Map<String, Object>> batch) throws JsonProcessingException {
    if (api == null) {
      throw new IllegalStateException("The event processor has no API wrapper.");
    }

    String payload = MapperFactory.getMapper()
        .writeValueAsString(Collections.singletonMap("events", batch));

    RequestBody body = RequestBody.create(payload, JSON_MEDIA_TYPE);
    return new Request.Builder()
        .url(eventsEndpoint)
        .post(body)
        .header(AUTH_HEADER, api.newPostRequest(eventsEndpoint, body).headers(AUTH_HEADER).get(0))
        .header(USER_AGENT_HEADER, SDK_USER_AGENT_PREFIX + Versions.getVersion())
        .header(SDK_USER_AGENT_HEADER, SDK_USER_AGENT_PREFIX + Versions.getVersion())
        .header(ACCEPT_HEADER, "application/json")
        .build();
  }

  /** Sends one batch, retrying transient failures. Runs on the sender thread. */
  private Outcome deliver(Request request, int batchSize) {
    for (int attempt = 1; ; attempt++) {
      if (attempt > 1) {
        try {
          Thread.sleep(jitter.applyAsLong(INITIAL_BACKOFF_MILLIS << (attempt - 2)));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return Outcome.DROPPED;
        }
      }
      if (cancelled) {
        return Outcome.DROPPED;
      }

      switch (attempt(request, batchSize)) {
        case ACCEPTED:
          return Outcome.SENT;
        case REJECTED:
          return Outcome.DROPPED;
        case UNAUTHORISED:
          return Outcome.UNAUTHORISED;
        case SERVER_ERROR:
          if (attempt == MAX_ATTEMPTS) {
            logger.error("Dropping " + batchSize + " events: the events API failed to take them "
                + MAX_ATTEMPTS + " times.");
            return Outcome.DROPPED;
          }
          break;
        default:
          if (attempt == MAX_ATTEMPTS) {
            logger.error("Could not reach the events API after " + MAX_ATTEMPTS + " attempts. "
                + "Keeping " + batchSize + " events for the next flush.");
            return Outcome.KEPT;
          }
      }
    }
  }

  private Attempt attempt(Request request, int batchSize) {
    Call call = client.newCall(request);
    currentCall = call;
    if (cancelled) {
      call.cancel();
    }

    try (Response response = call.execute()) {
      int status = response.code();
      if (response.isSuccessful()) {
        logRejections(response.body(), batchSize);
        return Attempt.ACCEPTED;
      }
      switch (status) {
        case 401:
        case 403:
          return Attempt.UNAUTHORISED;
        case 500:
          logger.info("The events API answered {} to {} events.", status, batchSize);
          return Attempt.SERVER_ERROR;
        case 408:
        case 429:
        case 502:
        case 503:
        case 504:
          logger.info("The events API answered {} to {} events.", status, batchSize);
          return Attempt.UNAVAILABLE;
        default:
          logger.error("Dropping " + batchSize + " events: the events API rejected them with "
              + status + ".");
          return Attempt.REJECTED;
      }
    } catch (IOException e) {
      logger.info("Failed to send {} events to the events API: {}", batchSize, e.toString());
      return Attempt.UNAVAILABLE;
    } finally {
      currentCall = null;
    }
  }

  private void logRejections(ResponseBody body, int batchSize) {
    JsonNode rejected;
    try {
      rejected = body == null ? null : MapperFactory.getMapper().readTree(body.string())
          .get("rejected");
    } catch (IOException | RuntimeException e) {
      return;
    }
    if (rejected != null && rejected.isArray() && rejected.size() > 0) {
      logger.error("The events API rejected " + rejected.size() + " of " + batchSize
          + " events. First rejection: " + rejected.get(0));
    }
  }

  private void settle(List<Map<String, Object>> batch, Outcome outcome,
      CompletableFuture<Void> tracked) {
    List<Map<String, Object>> next = null;
    CompletableFuture<Void> waiting;
    int droppedToReport = 0;

    synchronized (lock) {
      if (outcome == Outcome.UNAUTHORISED && disabled.compareAndSet(false, true)) {
        logger.error("The events API refused the environment key. Event tracking is stopped and "
            + (batch.size() + buffer.size()) + " events are dropped.");
      }
      if (disabled.get()) {
        clearBuffer();
      } else if (outcome == Outcome.KEPT && !cancelled) {
        buffer.addAll(0, batch);
        droppedToReport = dropOverflow();
      }
      // A kept batch means the events API is failing: retrying it whenever the buffer fills
      // would start a retry cycle per tracked event, so only a tick or a flush sends it again,
      // or, with the timer off, a full buffer once the cool-down has passed.
      fullBufferHeldSinceNanos = outcome == Outcome.KEPT ? System.nanoTime() : null;

      waiting = nextBatch;
      boolean requested = nextBatchRequested;
      nextBatch = null;
      nextBatchRequested = false;
      if (waiting != null && !buffer.isEmpty() && (requested || !isFullBufferHeld())) {
        next = takeBuffer();
        inFlight = waiting;
      } else {
        inFlight = null;
      }
    }

    logDrops(droppedToReport);
    tracked.complete(null);
    if (next != null) {
      send(next, waiting);
    } else if (waiting != null) {
      waiting.complete(null);
    }
  }

  /** Whether a full buffer must wait for the next flush. Call holding the lock. */
  private boolean isFullBufferHeld() {
    if (fullBufferHeldSinceNanos == null) {
      return false;
    }
    if (flushIntervalMillis > 0) {
      return true;
    }
    if (System.nanoTime() - fullBufferHeldSinceNanos
        < TimeUnit.MILLISECONDS.toNanos(holdCoolDownMillis)) {
      return true;
    }
    fullBufferHeldSinceNanos = null;
    return false;
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
      logger.error("Dropped the " + dropped + " oldest events: the buffer holds "
          + Math.max(maxBufferItems, MAX_BUFFERED_EVENTS) + " events. Further drops are reported"
          + " at most every " + TimeUnit.NANOSECONDS.toSeconds(DROP_LOG_INTERVAL_NANOS) + "s.");
    }
  }

  List<Map<String, Object>> bufferedEvents() {
    synchronized (lock) {
      return new ArrayList<>(buffer);
    }
  }
}
