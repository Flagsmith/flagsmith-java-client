package com.flagsmith.threads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import com.flagsmith.Versions;
import com.flagsmith.config.Retry;
import com.flagsmith.interfaces.FlagsmithSdk;
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
@Getter
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

  private final HttpUrl eventsEndpoint;
  private final int maxBufferItems;
  private final int flushIntervalMillis;
  private final int requestTimeoutMillis;
  private final List<Map<String, Object>> buffer = new ArrayList<>();
  private final Set<String> dedupeKeys = new HashSet<>();
  private final Object lock = new Object();
  private final ScheduledExecutorService scheduler;
  private final Set<CompletableFuture<Void>> inFlight = ConcurrentHashMap.newKeySet();
  private final RequestProcessor requestProcessor;
  @Setter
  private FlagsmithSdk api;
  private FlagsmithLogger logger = new FlagsmithLogger();
  private ScheduledFuture<?> scheduledFlush;

  /**
   * Instantiate with an HTTP client.
   *
   * @param client               client instance
   * @param eventsUri            base URI of the events API, e.g. https://events.api.flagsmith.com/
   * @param maxBufferItems       number of buffered events that triggers an immediate flush
   * @param flushIntervalMillis  interval between timed flushes; 0 disables the timer
   * @param requestTimeoutMillis how long {@link #close()} waits for each in-flight batch
   */
  public EventProcessor(OkHttpClient client, HttpUrl eventsUri, int maxBufferItems,
      int flushIntervalMillis, int requestTimeoutMillis) {
    this(eventsUri, maxBufferItems, flushIntervalMillis, requestTimeoutMillis,
        new RequestProcessor(client, new FlagsmithLogger(), buildRetry()));
  }

  /**
   * Instantiate with a request processor, for tests.
   *
   * @param eventsUri            base URI of the events API
   * @param maxBufferItems       number of buffered events that triggers an immediate flush
   * @param flushIntervalMillis  interval between timed flushes; 0 disables the timer
   * @param requestTimeoutMillis how long {@link #close()} waits for each in-flight batch
   * @param requestProcessor     request processor used to POST batches
   */
  public EventProcessor(HttpUrl eventsUri, int maxBufferItems, int flushIntervalMillis,
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
    try {
      List<Map<String, Object>> batch = null;

      synchronized (lock) {
        if (!buffer.isEmpty()) {
          batch = new ArrayList<>(buffer);
          buffer.clear();
        }
        dedupeKeys.clear();
      }

      if (batch != null) {
        send(batch);
      }
    } catch (RuntimeException e) {
      logger.error("Failed to flush events.", e);
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
    try {
      final String stringValue = value == null ? null : String.valueOf(value);

      Map<String, Object> eventMetadata = new HashMap<>();
      if (metadata != null) {
        eventMetadata.putAll(metadata);
      }
      eventMetadata.put(SDK_VERSION_KEY, Versions.getVersion());

      Map<String, Object> eventPayload = new LinkedHashMap<>();
      eventPayload.put("event", event);
      eventPayload.put("feature_name", featureName);
      eventPayload.put("identifier", identifier);
      eventPayload.put("value", stringValue);
      eventPayload.put("traits", traits);
      eventPayload.put("metadata", eventMetadata);
      eventPayload.put("timestamp", System.currentTimeMillis());

      boolean isFull;

      synchronized (lock) {
        if (dedupe && !dedupeKeys.add(dedupeKey(
            event, featureName, identifier, stringValue, eventMetadata.get(EXPERIMENT_ID_KEY)))) {
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

  private void send(List<Map<String, Object>> batch) {
    if (api == null) {
      logger.error("Dropping {} events: the event processor has no API wrapper.", batch.size());
      return;
    }

    String payload;

    try {
      payload = MapperFactory.getMapper()
          .writeValueAsString(Collections.singletonMap("events", batch));
    } catch (Exception e) {
      logger.error("Error parsing event data to JSON.", e);
      return;
    }

    Request request = api
        .newPostRequest(eventsEndpoint, RequestBody.create(payload, JSON_MEDIA_TYPE))
        .newBuilder()
        .header(SDK_USER_AGENT_HEADER, SDK_USER_AGENT_PREFIX + Versions.getVersion())
        .build();

    CompletableFuture<Void> tracked = new CompletableFuture<>();
    inFlight.add(tracked);

    requestProcessor
        .submit(request, new TypeReference<JsonNode>() {}, Boolean.FALSE, buildRetry())
        .whenComplete((response, error) -> {
          inFlight.remove(tracked);
          tracked.complete(null);
        });
  }

  private CompletableFuture<Void> awaitInFlight() {
    return CompletableFuture.allOf(inFlight.toArray(new CompletableFuture[0]));
  }
}
