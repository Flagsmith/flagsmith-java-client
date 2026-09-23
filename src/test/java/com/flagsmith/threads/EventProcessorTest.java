package com.flagsmith.threads;

import static okhttp3.mock.MediaTypes.MEDIATYPE_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.config.Retry;
import com.flagsmith.interfaces.FlagsmithSdk;
import com.flagsmith.models.TraitConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mock.MockInterceptor;
import okio.Buffer;
import org.mockito.invocation.Invocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EventProcessorTest {

  private static final String EVENTS_URI = "http://events-uri/";
  private static final String EVENTS_ENDPOINT = EVENTS_URI + "v1/events";
  private static final String ACCEPTED_BODY = "{\"accepted\": 1, \"rejected\": []}";
  /** Every wait in this test is bounded so a broken retry loop fails instead of hanging. */
  private static final long WAIT_SECONDS = 10L;

  private MockInterceptor interceptor;
  private RecordingInterceptor recorder;
  private FlagsmithSdk api;
  private EventProcessor eventProcessor;

  @BeforeEach
  public void init() {
    interceptor = new MockInterceptor();
    recorder = new RecordingInterceptor();
    api = mock(FlagsmithSdk.class);
    when(api.newPostRequest(any(), any())).thenAnswer((invocation) -> new Request.Builder()
        .url((HttpUrl) invocation.getArgument(0))
        .header("X-Environment-Key", "api-key")
        .header("User-Agent", "flagsmith-java-sdk/test")
        .addHeader("Accept", "application/json")
        .post((RequestBody) invocation.getArgument(1))
        .build());
  }

  @AfterEach
  public void tearDown() {
    if (eventProcessor != null) {
      // Appended last, so it only catches whatever close() flushes out of the buffer.
      interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);
      eventProcessor.close();
      eventProcessor = null;
    }
  }

  private EventProcessor newProcessor(int maxBufferItems, int flushIntervalMillis) {
    return newProcessor(maxBufferItems, flushIntervalMillis, null);
  }

  private EventProcessor newProcessor(
      int maxBufferItems, int flushIntervalMillis, Interceptor extra) {
    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder().addInterceptor(recorder);
    if (extra != null) {
      clientBuilder.addInterceptor(extra);
    }
    OkHttpClient client = clientBuilder.addInterceptor(interceptor).build();
    eventProcessor = new EventProcessor(
        HttpUrl.get(EVENTS_URI),
        maxBufferItems,
        flushIntervalMillis,
        new RequestProcessor(client, new FlagsmithLogger()));
    eventProcessor.setApi(api);
    return eventProcessor;
  }

  @SneakyThrows
  private void flushAndWait(EventProcessor processor) {
    processor.flush().get(WAIT_SECONDS, TimeUnit.SECONDS);
  }

  @Test
  public void trackEvent_buffersStringifiedValueSdkVersionAndTimestamp() {
    EventProcessor processor = newProcessor(1000, 0);

    Map<String, Object> traits = new HashMap<>();
    traits.put("plan", "premium");
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("source", "checkout");

    long before = System.currentTimeMillis();
    processor.trackEvent("purchase", "user-123", 49.0, traits, metadata);

    assertEquals(1, processor.bufferedEvents().size());

    Map<String, Object> event = processor.bufferedEvents().get(0);
    assertEquals(
        Arrays.asList("event", "feature_name", "identifier", "value", "traits", "metadata",
            "timestamp"),
        new ArrayList<>(event.keySet()));
    assertEquals("purchase", event.get("event"));
    assertNull(event.get("feature_name"));
    assertEquals("user-123", event.get("identifier"));
    assertEquals("49.0", event.get("value"));
    assertEquals(MapperFactory.getMapper().valueToTree(traits), event.get("traits"));

    JsonNode eventMetadata = (JsonNode) event.get("metadata");
    assertEquals("checkout", eventMetadata.get("source").asText());
    assertNotNull(eventMetadata.get("sdk_version"));

    long timestamp = (Long) event.get("timestamp");
    assertTrue(timestamp >= before);
  }

  @Test
  public void trackEvent_buffersNullValueAsNull() {
    EventProcessor processor = newProcessor(1000, 0);

    processor.trackEvent("purchase", "user-123", null, null, null);

    Map<String, Object> event = processor.bufferedEvents().get(0);
    assertNull(event.get("value"));
    assertNull(event.get("traits"));
  }

  @Test
  public void trackExposureEvent_dedupesIdenticalExposuresWithinTheFlushWindow() {
    EventProcessor processor = newProcessor(1000, 0);
    Map<String, Object> metadata = Collections.singletonMap("experiment_id", 42);

    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, metadata);
    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, metadata);

    assertEquals(1, processor.bufferedEvents().size());
  }

  @Test
  public void trackExposureEvent_doesNotDedupeWhenAnyKeyPartDiffers() {
    EventProcessor processor = newProcessor(1000, 0);
    Map<String, Object> metadata = Collections.singletonMap("experiment_id", 42);

    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, metadata);
    processor.trackExposureEvent("checkout_cta", "user-2", "treatment", null, metadata);
    processor.trackExposureEvent("checkout_cta", "user-1", "control", null, metadata);
    processor.trackExposureEvent("pricing_page", "user-1", "treatment", null, metadata);
    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null,
        Collections.singletonMap("experiment_id", 43));

    assertEquals(5, processor.bufferedEvents().size());
  }

  @Test
  public void trackExposureEvent_buffersAgainAfterAFlush() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);
    Map<String, Object> metadata = Collections.singletonMap("experiment_id", 42);

    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, metadata);
    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, metadata);
    flushAndWait(processor);

    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, metadata);

    assertEquals(1, processor.bufferedEvents().size());
    assertEquals(1, recorder.count());
  }

  @Test
  public void trackEvent_neverDedupesCustomEvents() {
    EventProcessor processor = newProcessor(1000, 0);

    processor.trackEvent("purchase", "user-1", "49.00", null, null);
    processor.trackEvent("purchase", "user-1", "49.00", null, null);

    assertEquals(2, processor.bufferedEvents().size());
  }

  @Test
  @SneakyThrows
  public void flush_postsTheBatchToTheEventsEndpoint() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule()
        .post(EVENTS_ENDPOINT)
        .headerMatches("X-Environment-Key", Pattern.compile("api-key"))
        .headerMatches("Flagsmith-SDK-User-Agent", Pattern.compile("flagsmith-java-sdk/.*"))
        .headerMatches("Content-Type", Pattern.compile("application/json; charset=utf-8"))
        .anyTimes()
        .respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null,
        Collections.singletonMap("experiment_id", 42));
    flushAndWait(processor);

    assertEquals(1, recorder.count());

    JsonNode body = MapperFactory.getMapper().readTree(recorder.bodies().get(0));
    assertEquals(1, body.get("events").size());

    JsonNode event = body.get("events").get(0);
    assertEquals("$flag_exposure", event.get("event").asText());
    assertEquals("checkout_cta", event.get("feature_name").asText());
    assertEquals("user-1", event.get("identifier").asText());
    assertEquals("treatment", event.get("value").asText());
    assertEquals(42, event.get("metadata").get("experiment_id").asInt());
    assertTrue(event.get("metadata").has("sdk_version"));
    assertTrue(event.get("timestamp").isNumber());
    assertTrue(processor.bufferedEvents().isEmpty());
  }

  @Test
  @SneakyThrows
  public void flush_doesNotPostWhenTheBufferIsEmpty() {
    EventProcessor processor = newProcessor(1000, 0);

    flushAndWait(processor);

    assertEquals(0, recorder.count());
  }

  @Test
  @SneakyThrows
  public void trackEvent_flushesWhenTheBufferIsFull() {
    EventProcessor processor = newProcessor(2, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    assertEquals(1, processor.bufferedEvents().size());

    processor.trackEvent("purchase", "user-2", "2", null, null);
    assertTrue(processor.bufferedEvents().isEmpty());

    flushAndWait(processor);

    assertEquals(1, recorder.count());
    JsonNode body = MapperFactory.getMapper().readTree(recorder.bodies().get(0));
    assertEquals(2, body.get("events").size());
  }

  @Test
  @SneakyThrows
  public void flush_completesOnlyAfterTheInFlightPostCompletes() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule()
        .post(EVENTS_ENDPOINT)
        .anyTimes()
        .delay(600)
        .respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("purchase", "user-1", "1", null, null);

    long start = System.currentTimeMillis();
    flushAndWait(processor);
    long elapsed = System.currentTimeMillis() - start;

    assertEquals(1, recorder.count());
    assertTrue(elapsed >= 500, "flush() returned after " + elapsed + "ms, before the POST");
  }

  @Test
  @SneakyThrows
  public void flush_retriesOnceOnServerErrorThenDelivers() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).times(1).respond(503);
    interceptor.addRule().post(EVENTS_ENDPOINT).times(1).respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(2, recorder.count());
    assertEquals(recorder.bodies().get(0), recorder.bodies().get(1));
  }

  @Test
  @SneakyThrows
  public void flush_dropsTheBatchAfterTwoServerErrors() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(500);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(2, recorder.count());
    assertTrue(processor.bufferedEvents().isEmpty());
  }

  @Test
  @SneakyThrows
  public void flush_doesNotRetryOnClientError() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(400);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(1, recorder.count());
    assertTrue(processor.bufferedEvents().isEmpty());
  }

  @Test
  @SneakyThrows
  public void flush_retriesOnceOnAConnectionFailureThenDelivers() {
    EventProcessor processor = newProcessor(1000, 0, new FailingInterceptor(1));
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(2, recorder.count());
    assertEquals(recorder.bodies().get(0), recorder.bodies().get(1));
  }

  @Test
  @SneakyThrows
  public void flush_dropsTheBatchAfterTwoConnectionFailures() {
    EventProcessor processor = newProcessor(1000, 0, new FailingInterceptor(Integer.MAX_VALUE));

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(2, recorder.count());
    assertTrue(processor.bufferedEvents().isEmpty());
  }

  @Test
  @SneakyThrows
  public void flush_waitsForABatchAnotherThreadIsAlreadySending() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    CountDownLatch insideSend = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer((invocation) -> {
      insideSend.countDown();
      release.await(WAIT_SECONDS, TimeUnit.SECONDS);
      return new Request.Builder()
          .url((HttpUrl) invocation.getArgument(0))
          .header("X-Environment-Key", "api-key")
          .post((RequestBody) invocation.getArgument(1))
          .build();
    }).when(api).newPostRequest(any(), any());

    processor.trackEvent("purchase", "user-1", "1", null, null);

    Thread sender = new Thread(processor::flush, "test-sender");
    sender.start();
    assertTrue(insideSend.await(WAIT_SECONDS, TimeUnit.SECONDS));

    // The buffer is already empty here, but the batch is still on its way out.
    CompletableFuture<Void> second = processor.flush();
    assertFalse(second.isDone(), "flush() completed while another thread was mid-send");

    release.countDown();
    second.get(WAIT_SECONDS, TimeUnit.SECONDS);
    sender.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

    assertEquals(1, recorder.count());
  }

  @Test
  @SneakyThrows
  public void flush_completesWhenBuildingTheRequestThrows() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);
    doThrow(new IllegalStateException("boom")).when(api).newPostRequest(any(), any());

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(0, recorder.count());
    // A later flush must not inherit a batch that was never settled.
    flushAndWait(processor);
  }

  @Test
  @SneakyThrows
  public void flush_completesWhenTheRequestProcessorIsAlreadyShutDown() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    processor.getRequestProcessor().close();

    flushAndWait(processor);

    assertEquals(0, recorder.count());
  }

  @Test
  @SneakyThrows
  public void trackEvent_isANoOpAfterClose() {
    EventProcessor processor = newProcessor(2, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.close();
    eventProcessor = null;
    int postsAfterClose = recorder.count();

    processor.trackEvent("purchase", "user-1", "1", null, null);
    processor.trackEvent("purchase", "user-2", "2", null, null);
    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, null);

    assertTrue(processor.bufferedEvents().isEmpty());
    flushAndWait(processor);
    assertEquals(postsAfterClose, recorder.count());
  }

  @Test
  public void trackExposureEvent_unwrapsTraitConfigsAndDropsTransientTraits() {
    EventProcessor processor = newProcessor(1000, 0);

    Map<String, Object> traits = new LinkedHashMap<>();
    traits.put("plan", "premium");
    traits.put("tier", new TraitConfig("gold", false));
    traits.put("session_id", new TraitConfig("abc123", true));

    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", traits, null);

    JsonNode buffered = (JsonNode) processor.bufferedEvents().get(0).get("traits");
    assertEquals(2, buffered.size());
    assertEquals("premium", buffered.get("plan").asText());
    assertEquals("gold", buffered.get("tier").asText());
    assertFalse(buffered.has("session_id"), "a transient trait reached the events API");
  }

  @Test
  public void trackEvent_copiesTraitsAndMetadataDeeplyAtBufferTime() {
    EventProcessor processor = newProcessor(1000, 0);

    Map<String, Object> traits = new LinkedHashMap<>();
    traits.put("plan", "premium");
    Map<String, Object> nested = new HashMap<>();
    nested.put("step", "payment");
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("context", nested);
    processor.trackEvent("purchase", "user-1", "1", traits, metadata);

    traits.put("plan", "mutated");
    traits.put("added_later", "nope");
    nested.put("step", "mutated");

    Map<String, Object> event = processor.bufferedEvents().get(0);
    assertEquals(
        MapperFactory.getMapper().valueToTree(Collections.singletonMap("plan", "premium")),
        event.get("traits"));
    assertEquals("payment",
        ((JsonNode) event.get("metadata")).get("context").get("step").asText());
  }

  @Test
  @SneakyThrows
  public void trackEvent_dropsOnlyTheEventWhoseValuesCannotBeSerialised() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    // Jackson has no serialiser for a bean without properties.
    processor.trackEvent("purchase", "user-2", "2",
        Collections.singletonMap("opaque", new Object()), null);
    processor.trackEvent("purchase", "user-3", "3", null,
        Collections.singletonMap("opaque", new Object()));
    processor.trackEvent("purchase", "user-4", "4", null, null);

    assertEquals(2, processor.bufferedEvents().size());
    flushAndWait(processor);

    assertEquals(1, recorder.count());
    JsonNode events = MapperFactory.getMapper().readTree(recorder.bodies().get(0)).get("events");
    assertEquals(2, events.size());
    assertEquals("user-1", events.get(0).get("identifier").asText());
    assertEquals("user-4", events.get(1).get("identifier").asText());
  }

  @Test
  @SneakyThrows
  public void start_flushesOnTheTimer() {
    EventProcessor processor = newProcessor(1000, 100);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.start();
    processor.trackEvent("purchase", "user-1", "1", null, null);

    assertTrue(recorder.awaitFirstRequest(WAIT_SECONDS), "the timer never flushed");
    assertTrue(processor.bufferedEvents().isEmpty());
  }

  @Test
  @SneakyThrows
  public void close_flushesAndStopsTheSchedulerThread() {
    EventProcessor processor = newProcessor(1000, 500);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.start();
    processor.trackEvent("purchase", "user-1", "1", null, null);
    processor.close();
    eventProcessor = null;

    assertEquals(1, recorder.count());
    assertTrue(processor.getScheduler().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
    assertTrue(processor.getScheduler().isTerminated());
  }

  @Test
  @SneakyThrows
  public void start_isANoOpAfterClose() {
    EventProcessor processor = newProcessor(1000, 100);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);
    processor.close();
    eventProcessor = null;

    // Scheduling on the shut-down scheduler would throw RejectedExecutionException.
    processor.start();

    assertTrue(processor.getScheduler().isShutdown());
  }

  @Test
  public void worstCaseBatchMillis_coversEveryAttemptAtTheClientTimeoutsPlusBackoff() {
    OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(1000, TimeUnit.MILLISECONDS)
        .writeTimeout(2000, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .build();
    Retry retry = new Retry(2);

    // Two attempts of connect + write + read, and the 200ms backoff before the second.
    assertEquals(2 * 6000 + 200, EventProcessor.worstCaseBatchMillis(client, retry));
  }

  @Test
  public void worstCaseBatchMillis_prefersTheCallTimeout() {
    OkHttpClient client = new OkHttpClient.Builder()
        .callTimeout(4000, TimeUnit.MILLISECONDS)
        .build();

    assertEquals(2 * 4000 + 200,
        EventProcessor.worstCaseBatchMillis(client, new Retry(2)));
  }

  @Test
  public void worstCaseBatchMillis_isUnboundedWhenATimeoutIsOff() {
    OkHttpClient client = new OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build();

    assertEquals(EventProcessor.UNBOUNDED,
        EventProcessor.worstCaseBatchMillis(client, new Retry(2)));
  }

  @Test
  @SneakyThrows
  public void close_stopsWaitingAtTheWorstCaseBound() {
    // A call timeout of 100ms bounds a batch at 2 x 100ms + 200ms backoff. The hung API below
    // ignores the cancellation, as a stuck interceptor or proxy would.
    AcceptingInterceptor eventsApi = AcceptingInterceptor.blocked();
    OkHttpClient client = new OkHttpClient.Builder()
        .callTimeout(100, TimeUnit.MILLISECONDS)
        .addInterceptor(eventsApi)
        .build();
    EventProcessor processor = new EventProcessor(
        HttpUrl.get(EVENTS_URI), 1000, 0, new RequestProcessor(client, new FlagsmithLogger()));
    processor.setApi(api);
    FlagsmithLogger logger = mock(FlagsmithLogger.class);
    processor.setLogger(logger);
    assertEquals(400, processor.getCloseTimeoutMillis());

    try {
      processor.trackEvent("purchase", "user-1", "1", null, null);
      long start = System.nanoTime();
      processor.close();
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      verify(logger).error(contains("Stopped waiting"));
      assertTrue(elapsedMillis < TimeUnit.SECONDS.toMillis(WAIT_SECONDS) / 2,
          "close() waited " + elapsedMillis + "ms");
    } finally {
      eventsApi.release();
    }
  }

  @Test
  public void closeTimeout_followsTheConfiguredTimeouts() {
    FlagsmithConfig config = FlagsmithConfig.newBuilder()
        .connectTimeout(1000)
        .writeTimeout(2000)
        .readTimeout(30000)
        .withEnableEvents(Boolean.TRUE)
        .build();

    // The read timeout the caller configured, not the SDK default.
    assertEquals(2 * (1000 + 2000 + 30000) + 200,
        config.getEventProcessor().getCloseTimeoutMillis());
  }

  @Test
  public void trackEvent_neverThrowsWhenTheApiIsMissing() {
    EventProcessor processor = newProcessor(1, 0);
    processor.setApi(null);

    processor.trackEvent("purchase", "user-1", "1", null, null);

    assertEquals(0, recorder.count());
    assertTrue(processor.bufferedEvents().isEmpty());
  }

  @Test
  @SneakyThrows
  public void flush_dropsEventsBeyondTheInFlightLimitInsteadOfQueueingThem() {
    AcceptingInterceptor eventsApi = AcceptingInterceptor.blocked();
    EventProcessor processor = newProcessor(1000, 0, eventsApi);
    FlagsmithLogger logger = mock(FlagsmithLogger.class);
    processor.setLogger(logger);

    // The events API hangs, so every batch stays in flight until it is released. Each full
    // buffer flushes itself, which puts exactly the limit in flight.
    for (int i = 0; i < EventProcessor.MAX_IN_FLIGHT_EVENTS; i++) {
      processor.trackEvent("purchase", "user-" + i, "1", null, null);
    }
    processor.trackEvent("purchase", "one-too-many", "1", null, null);
    CompletableFuture<Void> all = processor.flush();

    assertTrue(processor.bufferedEvents().isEmpty(), "the dropped batch stayed in the buffer");
    verify(logger).error(contains("Dropped 1 events"));
    assertFalse(all.isDone());

    // A caller saturating the processor does not get an error line per flush.
    for (int i = 0; i < 100; i++) {
      processor.trackEvent("purchase", "also-dropped-" + i, "1", null, null);
      processor.flush();
    }
    verify(logger, times(1)).error(startsWith("Dropped"));

    eventsApi.release();
    all.get(WAIT_SECONDS, TimeUnit.SECONDS);

    assertEquals(EventProcessor.MAX_IN_FLIGHT_EVENTS, deliveredEvents());
    for (String body : recorder.bodies()) {
      assertFalse(body.contains("one-too-many"));
      assertFalse(body.contains("also-dropped"));
    }

    // Once the backlog clears, batches flow again.
    processor.trackEvent("purchase", "after", "1", null, null);
    flushAndWait(processor);
    assertEquals(EventProcessor.MAX_IN_FLIGHT_EVENTS + 1, deliveredEvents());
  }

  @Test
  @SneakyThrows
  public void flush_neverDropsEventsOnAHealthyApiWithASmallBuffer() {
    // A one-event buffer sends a batch per event. Capping batches rather than events throttled
    // exactly this configuration, dropping most events even against an instant API.
    EventProcessor processor = newProcessor(1, 0, AcceptingInterceptor.open());
    FlagsmithLogger logger = mock(FlagsmithLogger.class);
    processor.setLogger(logger);
    int events = 2000;

    for (int i = 0; i < events; i++) {
      processor.trackEvent("purchase", "user-" + i, "1", null, null);
    }
    flushAndWait(processor);

    assertEquals(events, deliveredEvents());
    assertEquals(Collections.emptyList(), errorCalls(logger));
  }

  /**
   * Every error-level call made on a mocked logger, whatever its arguments. Matching on the
   * invocations rather than with verify(...) avoids Mockito's varargs matching, under which a
   * matcher list silently misses calls with a different number of arguments.
   */
  private static List<String> errorCalls(FlagsmithLogger logger) {
    List<String> calls = new ArrayList<>();
    for (Invocation invocation : mockingDetails(logger).getInvocations()) {
      String method = invocation.getMethod().getName();
      if (method.equals("error") || method.equals("httpError")) {
        calls.add(invocation.toString());
      }
    }
    return calls;
  }

  /** The number of events across every request the events API received. */
  @SneakyThrows
  private int deliveredEvents() {
    int delivered = 0;
    for (String body : recorder.bodies()) {
      delivered += MapperFactory.getMapper().readTree(body).get("events").size();
    }
    return delivered;
  }

  @Test
  @SneakyThrows
  public void flush_logsEventsTheApiRejects() {
    EventProcessor processor = newProcessor(1000, 0);
    FlagsmithLogger logger = mock(FlagsmithLogger.class);
    processor.setLogger(logger);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(
        "{\"accepted\": 1, \"rejected\": [{\"index\": 1, \"error\": \"event too long\"}]}",
        MEDIATYPE_JSON);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    processor.trackEvent("purchase", "user-2", "2", null, null);
    flushAndWait(processor);

    verify(logger).error(contains("rejected 1 of 2 events"));
    verify(logger).error(contains("event too long"));
  }

  @Test
  @SneakyThrows
  public void flush_logsNothingWhenEveryEventIsAccepted() {
    EventProcessor processor = newProcessor(1000, 0);
    FlagsmithLogger logger = mock(FlagsmithLogger.class);
    processor.setLogger(logger);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(1, recorder.count());
    assertEquals(Collections.emptyList(), errorCalls(logger));
  }

  /**
   * Accepts every request, optionally holding each one until released, like an events API that
   * has stopped answering. It answers itself rather than deferring to the MockInterceptor, whose
   * canned response bodies share one buffer and break under concurrent calls.
   */
  private static class AcceptingInterceptor implements Interceptor {

    private final CountDownLatch released;

    private AcceptingInterceptor(boolean blocked) {
      this.released = new CountDownLatch(blocked ? 1 : 0);
    }

    static AcceptingInterceptor blocked() {
      return new AcceptingInterceptor(true);
    }

    static AcceptingInterceptor open() {
      return new AcceptingInterceptor(false);
    }

    void release() {
      released.countDown();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      try {
        released.await(WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException(e);
      }
      return new Response.Builder()
          .request(chain.request())
          .protocol(Protocol.HTTP_1_1)
          .code(202)
          .message("Accepted")
          .body(ResponseBody.create(ACCEPTED_BODY, MediaType.get("application/json")))
          .build();
    }
  }

  /** Records every request that reaches the network, with its body. */
  private static class RecordingInterceptor implements Interceptor {

    private final List<String> bodies = Collections.synchronizedList(new ArrayList<>());
    private final CountDownLatch firstRequest = new CountDownLatch(1);

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request request = chain.request();
      Buffer buffer = new Buffer();
      if (request.body() != null) {
        request.body().writeTo(buffer);
      }
      bodies.add(buffer.readUtf8());
      firstRequest.countDown();
      return chain.proceed(request);
    }

    int count() {
      return bodies.size();
    }

    List<String> bodies() {
      return new ArrayList<>(bodies);
    }

    boolean awaitFirstRequest(long seconds) throws InterruptedException {
      return firstRequest.await(seconds, TimeUnit.SECONDS);
    }
  }

  /** Simulates a connection failure for the first n attempts. */
  private static class FailingInterceptor implements Interceptor {

    private final AtomicInteger remainingFailures;

    FailingInterceptor(int failures) {
      this.remainingFailures = new AtomicInteger(failures);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      if (remainingFailures.getAndDecrement() > 0) {
        throw new IOException("connection refused");
      }
      return chain.proceed(chain.request());
    }
  }
}
