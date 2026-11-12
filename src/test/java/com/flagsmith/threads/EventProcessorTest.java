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
import com.fasterxml.jackson.databind.util.RawValue;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import com.flagsmith.config.FlagsmithConfig;
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
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

public class EventProcessorTest {

  private static final String EVENTS_URI = "http://events-uri/";
  private static final String EVENTS_ENDPOINT = EVENTS_URI + "v1/events";
  private static final String ACCEPTED_BODY = "{\"accepted\": 1, \"rejected\": []}";
  private static final long WAIT_SECONDS = 10L;

  private MockInterceptor interceptor;
  private RecordingInterceptor recorder;
  private List<Long> backoffs;
  private FlagsmithSdk api;
  private EventProcessor eventProcessor;

  @BeforeEach
  public void init() {
    interceptor = new MockInterceptor();
    recorder = new RecordingInterceptor();
    backoffs = Collections.synchronizedList(new ArrayList<>());
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
    // Records each backoff and retries at once, so retry tests do not wait for real backoffs.
    eventProcessor = new EventProcessor(
        HttpUrl.get(EVENTS_URI),
        maxBufferItems,
        flushIntervalMillis,
        client,
        (backoff) -> {
          backoffs.add(backoff);
          return 0;
        });
    eventProcessor.setApi(api);
    return eventProcessor;
  }

  @SneakyThrows
  private void flushAndWait(EventProcessor processor) {
    processor.flush().get(WAIT_SECONDS, TimeUnit.SECONDS);
  }

  private static FlagsmithLogger mockLogger(EventProcessor processor) {
    FlagsmithLogger logger = mock(FlagsmithLogger.class);
    processor.setLogger(logger);
    return logger;
  }

  /** Parse a buffered event's pre-serialised traits or metadata. */
  @SneakyThrows
  private static JsonNode json(Object buffered) {
    return MapperFactory.getMapper().readTree(((RawValue) buffered).rawValue().toString());
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
    assertEquals(MapperFactory.getMapper().valueToTree(traits), json(event.get("traits")));

    JsonNode eventMetadata = json(event.get("metadata"));
    assertEquals("checkout", eventMetadata.get("source").asText());
    assertNotNull(eventMetadata.get("sdk_version"));

    long timestamp = (Long) event.get("timestamp");
    assertTrue(timestamp >= before);

    processor.trackEvent("purchase", "user-123", null, null, null);
    assertNull(processor.bufferedEvents().get(1).get("value"));
    assertNull(processor.bufferedEvents().get(1).get("traits"));

    processor.trackEvent("purchase", "user-123", null, new HashMap<>(), null);
    assertNull(processor.bufferedEvents().get(2).get("traits"));
  }

  @Test
  public void trackExposureEvent_dedupesOnlyEqualExposuresWithinTheFlushWindow() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);
    Map<String, Object> metadata = Collections.singletonMap("experiment_id", 42);

    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, metadata);
    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, metadata);
    assertEquals(1, processor.bufferedEvents().size());

    processor.trackExposureEvent("checkout_cta", "user-2", "treatment", null, metadata);
    processor.trackExposureEvent("checkout_cta", "user-1", "control", null, metadata);
    processor.trackExposureEvent("pricing_page", "user-1", "treatment", null, metadata);
    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null,
        Collections.singletonMap("experiment_id", 43));
    processor.trackExposureEvent("checkout_cta", "a\u0000b", "c", null, metadata);
    processor.trackExposureEvent("checkout_cta", "a", "b\u0000c", null, metadata);
    assertEquals(7, processor.bufferedEvents().size());

    processor.trackEvent("purchase", "user-1", "49.00", null, null);
    processor.trackEvent("purchase", "user-1", "49.00", null, null);
    assertEquals(9, processor.bufferedEvents().size());

    flushAndWait(processor);
    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, metadata);
    assertEquals(1, processor.bufferedEvents().size());
    assertEquals(1, recorder.count());
  }

  @Test
  @SneakyThrows
  public void flush_postsTheBatchToTheEventsEndpoint() {
    EventProcessor processor = newProcessor(1000, 0);
    flushAndWait(processor);
    assertEquals(0, recorder.count());

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

  /**
   * The first {@code failures} attempts fail with {@code status}, or with a connection failure
   * when it is empty; the rest are accepted.
   */
  @ParameterizedTest
  @CsvSource({
      "503, 2, 3", "502, 1, 2", "504, 1, 2", "408, 1, 2", "429, 1, 2", "500, 2, 3",
      "400, 1, 1", "404, 1, 1", "501, 1, 1",
      ", 1, 2", ", 2, 3"})
  @SneakyThrows
  public void flush_retriesTransientFailuresAndDropsRejectedBatches(
      Integer status, int failures, int expectedAttempts) {
    EventProcessor processor = status == null
        ? newProcessor(1000, 0, new FailingInterceptor(failures))
        : newProcessor(1000, 0);
    if (status != null) {
      interceptor.addRule().post(EVENTS_ENDPOINT).times(failures).respond(status);
    }
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(expectedAttempts, recorder.count());
    assertEquals(recorder.bodies().get(0), recorder.bodies().get(expectedAttempts - 1));
    assertTrue(processor.bufferedEvents().isEmpty());
  }

  @Test
  public void flush_backsOffFromOneSecondDoublingBeforeEachRetry() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).times(2).respond(503);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(3, recorder.count());
    assertEquals(Arrays.asList(1_000L, 2_000L), backoffs);
  }

  @Test
  public void fullJitter_waitsAnythingFromNoneUpToTheBackoff() {
    for (int i = 0; i < 1000; i++) {
      long wait = EventProcessor.fullJitter(1_000L);
      assertTrue(wait >= 0 && wait <= 1_000L, "waited " + wait + "ms");
    }
    assertEquals(0L, EventProcessor.fullJitter(0L));
  }

  /**
   * Every attempt fails with the given responses, a status code or, when empty, a connection
   * failure. The last one decides: a 500 drops the batch, any other transient failure keeps it.
   */
  @ParameterizedTest
  @CsvSource({"503, 503, 503, true", "500, 500, 503, true", "503, 503, 500, false",
      ", , , true", "500, 500, , true"})
  @SneakyThrows
  public void flush_keepsABatchForTheNextFlushUnlessItsLastAttemptGetsA500(
      Integer first, Integer second, Integer last, boolean kept) {
    EventProcessor processor = newProcessor(1000, 0, failingWith(first, second, last));
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("add_to_cart", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(EventProcessor.MAX_ATTEMPTS, recorder.count());
    assertEquals(kept ? 1 : 0, processor.bufferedEvents().size());

    processor.trackEvent("checkout", "user-1", "42", null, null);
    flushAndWait(processor);

    assertEquals(EventProcessor.MAX_ATTEMPTS + 1, recorder.count());
    JsonNode events = MapperFactory.getMapper()
        .readTree(recorder.bodies().get(EventProcessor.MAX_ATTEMPTS)).get("events");
    List<String> sent = new ArrayList<>();
    events.forEach((event) -> sent.add(event.get("event").asText()));
    assertEquals(kept ? Arrays.asList("add_to_cart", "checkout")
        : Collections.singletonList("checkout"), sent);
    assertTrue(processor.bufferedEvents().isEmpty());
  }

  /** Answers each request with the next status, or fails its connection on null. */
  private static Interceptor failingWith(Integer... responses) {
    AtomicInteger next = new AtomicInteger();
    return (chain) -> {
      int index = next.getAndIncrement();
      if (index >= responses.length) {
        return chain.proceed(chain.request());
      }
      if (responses[index] == null) {
        throw new IOException("connection reset");
      }
      return new Response.Builder()
          .request(chain.request())
          .protocol(Protocol.HTTP_1_1)
          .code(responses[index])
          .message("Failed")
          .body(ResponseBody.create("{}", MediaType.get("application/json")))
          .build();
    };
  }

  @ParameterizedTest
  @CsvSource({"401", "403"})
  public void flush_stopsEventTrackingWhenTheEventsApiRefusesTheKey(int status) {
    EventProcessor processor = newProcessor(1000, 0);
    FlagsmithLogger logger = mockLogger(processor);
    interceptor.addRule().post(EVENTS_ENDPOINT).times(1).respond(status);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("add_to_cart", "user-1", "1", null, null);
    flushAndWait(processor);
    processor.trackEvent("checkout", "user-1", "42", null, null);
    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, null);
    flushAndWait(processor);

    assertEquals(1, recorder.count());
    assertTrue(processor.bufferedEvents().isEmpty());
    verify(logger, times(1)).error(contains("refused the environment key"));
  }

  /** Fails the first {@link EventProcessor#MAX_ATTEMPTS} requests once released, then accepts. */
  private static Interceptor failingOneBatchOnceReleased(CountDownLatch release) {
    AtomicInteger requests = new AtomicInteger();
    return (chain) -> {
      if (requests.incrementAndGet() > EventProcessor.MAX_ATTEMPTS) {
        return AcceptingInterceptor.accepted(chain.request());
      }
      try {
        release.await(WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException(e);
      }
      throw new IOException("connection reset");
    };
  }

  /** Every event sent, in the order sent, across every request. */
  @SneakyThrows
  private List<String> sentEvents() {
    List<String> sent = new ArrayList<>();
    for (String body : recorder.bodies()) {
      MapperFactory.getMapper().readTree(body).get("events")
          .forEach((event) -> sent.add(event.get("event").asText()));
    }
    return sent;
  }

  @Test
  @SneakyThrows
  public void flush_sendsTheEventsBehindAKeptBatchWithItWhenRequestedWhileItWasInFlight() {
    CountDownLatch release = new CountDownLatch(1);
    EventProcessor processor = newProcessor(1000, 0, failingOneBatchOnceReleased(release));
    mockLogger(processor);

    processor.trackEvent("add_to_cart", "user-1", "1", null, null);
    CompletableFuture<Void> first = processor.flush();
    processor.trackEvent("checkout", "user-1", "42", null, null);
    CompletableFuture<Void> second = processor.flush();
    release.countDown();

    first.get(WAIT_SECONDS, TimeUnit.SECONDS);
    second.get(WAIT_SECONDS, TimeUnit.SECONDS);
    assertEquals(EventProcessor.MAX_ATTEMPTS + 1, recorder.count());
    assertEquals(Arrays.asList("add_to_cart", "add_to_cart", "add_to_cart", "add_to_cart",
        "checkout"), sentEvents());
    assertTrue(processor.bufferedEvents().isEmpty());
  }

  @Test
  @SneakyThrows
  public void close_sendsTheEventsBehindAKeptBatchWithIt() {
    CountDownLatch release = new CountDownLatch(1);
    EventProcessor processor = newProcessor(1000, 0, failingOneBatchOnceReleased(release));
    eventProcessor = null;
    FlagsmithLogger logger = mockLogger(processor);

    processor.trackEvent("add_to_cart", "user-1", "1", null, null);
    processor.flush();
    processor.trackEvent("checkout", "user-1", "42", null, null);
    CompletableFuture<Void> closed = CompletableFuture.runAsync(() -> processor.close());
    release.countDown();
    closed.get(WAIT_SECONDS, TimeUnit.SECONDS);

    assertEquals(Arrays.asList("add_to_cart", "add_to_cart", "add_to_cart", "add_to_cart",
        "checkout"), sentEvents());
    verify(logger, never()).error(contains("left unsent on close"));
  }

  @Test
  @SneakyThrows
  public void trackEvent_holdsAFullBufferAfterAKeptBatchUntilTheNextFlush() {
    EventProcessor processor = newProcessor(2, 0, failingWith(503, 503, 503));
    mockLogger(processor);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.trackEvent("event_1", "user-1", null, null, null);
    processor.trackEvent("event_2", "user-1", null, null, null);
    flushAndWait(processor);
    assertEquals(EventProcessor.MAX_ATTEMPTS, recorder.count());

    for (int i = 3; i <= 6; i++) {
      processor.trackEvent("event_" + i, "user-1", null, null, null);
    }
    assertEquals(EventProcessor.MAX_ATTEMPTS, recorder.count());
    assertEquals(6, processor.bufferedEvents().size());

    flushAndWait(processor);
    assertEquals(EventProcessor.MAX_ATTEMPTS + 1, recorder.count());
    assertTrue(processor.bufferedEvents().isEmpty());

    // Once a batch gets through, a full buffer is sent at once again.
    processor.trackEvent("event_7", "user-1", null, null, null);
    processor.trackEvent("event_8", "user-1", null, null, null);
    assertTrue(processor.bufferedEvents().isEmpty());
    flushAndWait(processor);
    assertEquals(EventProcessor.MAX_ATTEMPTS + 2, recorder.count());
  }

  @Test
  @SneakyThrows
  public void trackEvent_holdsABufferThatFilledBehindABatchThatIsThenKept() {
    CountDownLatch release = new CountDownLatch(1);
    EventProcessor processor = newProcessor(2, 0, failingOneBatchOnceReleased(release));
    mockLogger(processor);

    processor.trackEvent("event_1", "user-1", null, null, null);
    processor.trackEvent("event_2", "user-1", null, null, null);
    CompletableFuture<Void> inFlight = processor.flush();
    processor.trackEvent("event_3", "user-1", null, null, null);
    processor.trackEvent("event_4", "user-1", null, null, null);
    release.countDown();
    inFlight.get(WAIT_SECONDS, TimeUnit.SECONDS);

    assertEquals(EventProcessor.MAX_ATTEMPTS, recorder.count());
    assertEquals(4, processor.bufferedEvents().size());
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

  private static Stream<Consumer<EventProcessorTest>> brokenSends() {
    return Stream.of(
        (test) -> doThrow(new IllegalStateException("boom")).when(test.api)
            .newPostRequest(any(), any()),
        (test) -> test.eventProcessor.getSender().shutdown(),
        (test) -> test.eventProcessor.setApi(null));
  }

  @ParameterizedTest
  @MethodSource("brokenSends")
  public void trackEvent_dropsTheBatchWithoutThrowingWhenItCannotBeSent(
      Consumer<EventProcessorTest> breakSend) {
    EventProcessor processor = newProcessor(1, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);
    breakSend.accept(this);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(0, recorder.count());
    assertTrue(processor.bufferedEvents().isEmpty());
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
  @SneakyThrows
  public void trackEvent_refusesAnEventThatRacesCloseInsteadOfStrandingIt() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    // Serialising the traits happens after the first closed check and before the buffer lock,
    // so a getter that blocks parks the tracking thread exactly in that window.
    CountDownLatch serialising = new CountDownLatch(1);
    CountDownLatch proceed = new CountDownLatch(1);
    Object slowTrait = new Object() {
      @SuppressWarnings("unused")
      public String getValue() throws InterruptedException {
        serialising.countDown();
        proceed.await(WAIT_SECONDS, TimeUnit.SECONDS);
        return "slow";
      }
    };
    Thread tracker = new Thread(() -> processor.trackEvent(
        "purchase", "user-1", "1", Collections.singletonMap("slow", slowTrait), null));
    tracker.start();
    assertTrue(serialising.await(WAIT_SECONDS, TimeUnit.SECONDS));

    processor.close();
    eventProcessor = null;
    proceed.countDown();
    tracker.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

    assertTrue(processor.bufferedEvents().isEmpty(), "an event was stranded after close()");
    assertEquals(0, recorder.count());
  }

  @Test
  public void trackExposureEvent_unwrapsTraitConfigsAndDropsTransientTraits() {
    EventProcessor processor = newProcessor(1000, 0);

    Map<String, Object> traits = new LinkedHashMap<>();
    traits.put("plan", "premium");
    traits.put("tier", new TraitConfig("gold", false));
    traits.put("session_id", new TraitConfig("abc123", true));

    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", traits, null);

    JsonNode buffered = json(processor.bufferedEvents().get(0).get("traits"));
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
        json(event.get("traits")));
    assertEquals("payment",
        json(event.get("metadata")).get("context").get("step").asText());
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
    Map<String, Object> cyclic = new HashMap<>();
    cyclic.put("self", cyclic);
    List<Object> cyclicList = new ArrayList<>();
    cyclicList.add(cyclicList);
    processor.trackEvent("purchase", "user-5", "5", cyclic, null);
    processor.trackEvent("purchase", "user-6", "6", null,
        Collections.singletonMap("list", cyclicList));
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

    processor.start();

    assertTrue(processor.getScheduler().isShutdown());
  }

  @Test
  public void eventProcessor_timesOutEachRequestAfterThreeSecondsAndRetriesItself() {
    OkHttpClient client = FlagsmithConfig.newBuilder().readTimeout(30_000).build().getHttpClient();
    EventProcessor processor = new EventProcessor(client, HttpUrl.get(EVENTS_URI), 1, 0);

    assertEquals(3_000, processor.getClient().callTimeoutMillis());
    assertFalse(processor.getClient().retryOnConnectionFailure());
    // Three attempts timing out, after waiting up to 1s, then 2s, before the retries.
    assertEquals(3 * 3_000 + 1_000 + 2_000, processor.getCloseTimeoutMillis());
    processor.close();
  }


  @Test
  @SneakyThrows
  public void close_sendsTheEventsBufferedBehindTheBatchInFlight() {
    AcceptingInterceptor eventsApi = AcceptingInterceptor.blocked();
    EventProcessor processor = newProcessor(1, 0, eventsApi);
    eventProcessor = null;
    FlagsmithLogger logger = mockLogger(processor);

    processor.trackEvent("purchase", "user-0", "1", null, null);
    processor.trackEvent("purchase", "user-1", "1", null, null);
    assertTrue(recorder.awaitCount(1));
    assertEquals(1, processor.bufferedEvents().size());

    CompletableFuture<Void> closed = CompletableFuture.runAsync(processor::close);
    eventsApi.release();
    closed.get(WAIT_SECONDS, TimeUnit.SECONDS);

    assertEquals(2, recorder.count());
    assertEquals(2, deliveredEvents());
    assertEquals(Collections.emptyList(), errorCalls(logger));
  }

  @Test
  @SneakyThrows
  public void close_stopsWaitingAtTheTimeout() {
    AcceptingInterceptor eventsApi = AcceptingInterceptor.blocked();
    EventProcessor processor = newProcessor(1000, 0, eventsApi);
    eventProcessor = null;
    processor.setCloseTimeoutMillis(400);
    FlagsmithLogger logger = mockLogger(processor);

    try {
      processor.trackEvent("purchase", "user-1", "1", null, null);
      long start = System.nanoTime();
      processor.close();
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      verify(logger).error(contains("Stopped waiting"));
      assertTrue(elapsedMillis >= 400, "close() returned after " + elapsedMillis + "ms");
      assertTrue(elapsedMillis < TimeUnit.SECONDS.toMillis(WAIT_SECONDS) / 2,
          "close() waited " + elapsedMillis + "ms");
    } finally {
      eventsApi.release();
    }
  }

  @Test
  @SneakyThrows
  public void close_abandonsTheRequestInFlightAtItsTimeout() {
    CountDownLatch requested = new CountDownLatch(1);
    CompletableFuture<Void> abandoned = new CompletableFuture<>();
    EventProcessor processor = newProcessor(1000, 0, (chain) -> {
      requested.countDown();
      try {
        Thread.sleep(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      abandoned.complete(null);
      throw new IOException("Canceled");
    });
    eventProcessor = null;
    mockLogger(processor);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    processor.flush();
    assertTrue(requested.await(WAIT_SECONDS, TimeUnit.SECONDS));
    long start = System.nanoTime();
    processor.close(300);
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertTrue(elapsedMillis < 2_000, "close(300) waited " + elapsedMillis + "ms");
    abandoned.get(WAIT_SECONDS / 2, TimeUnit.SECONDS);
    assertTrue(processor.getSender().awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
    assertEquals(1, recorder.count());
    assertTrue(processor.bufferedEvents().isEmpty());
  }

  @Test
  @SneakyThrows
  public void settle_sendsEventsThatWaitedBehindTheBatchInFlight() {
    AcceptingInterceptor eventsApi = AcceptingInterceptor.blocked();
    EventProcessor processor = newProcessor(1000, 0, eventsApi);
    FlagsmithLogger logger = mockLogger(processor);

    processor.trackEvent("purchase", "user-0", "1", null, null);
    processor.flush();
    for (int i = 0; i < 100; i++) {
      processor.trackEvent("purchase", "waiting-" + i, "1", null, null);
      processor.flush();
    }

    assertTrue(recorder.awaitCount(1));
    assertEquals(100, processor.bufferedEvents().size());

    eventsApi.release();

    assertTrue(awaitDelivered(1 + 100));
    assertEquals(2, recorder.count());
    assertEquals(Collections.emptyList(), errorCalls(logger));
  }

  @Test
  @SneakyThrows
  public void flush_waitsForTheEventsBufferedBehindTheBatchInFlight() {
    CountDownLatch inFlightReleased = new CountDownLatch(1);
    CountDownLatch waitingReleased = new CountDownLatch(1);
    EventProcessor processor = newProcessor(1000, 0, (chain) -> {
      Buffer body = new Buffer();
      chain.request().body().writeTo(body);
      try {
        (body.readUtf8().contains("waiting") ? waitingReleased : inFlightReleased)
            .await(WAIT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException(e);
      }
      return AcceptingInterceptor.accepted(chain.request());
    });

    processor.trackEvent("purchase", "user-0", "1", null, null);
    processor.flush();
    processor.trackEvent("purchase", "waiting", "1", null, null);
    CompletableFuture<Void> flushed = processor.flush();

    assertTrue(recorder.awaitCount(1));
    inFlightReleased.countDown();
    assertTrue(recorder.awaitCount(2));
    assertFalse(flushed.isDone());

    waitingReleased.countDown();
    flushed.get(WAIT_SECONDS, TimeUnit.SECONDS);
    assertEquals(2, deliveredEvents());
  }

  @Test
  @SneakyThrows
  public void trackEvent_dropsTheOldestEventsOnceTheBufferIsFull() {
    AcceptingInterceptor eventsApi = AcceptingInterceptor.blocked();
    EventProcessor processor = newProcessor(1000, 0, eventsApi);
    FlagsmithLogger logger = mockLogger(processor);
    int inFlight = 1000;

    for (int i = 0; i < inFlight; i++) {
      processor.trackEvent("purchase", "user-" + i, "1", null, null);
    }
    for (int i = 0; i < EventProcessor.MAX_BUFFERED_EVENTS + 500; i++) {
      processor.trackEvent("purchase", "overflow-" + i + "-", "1", null, null);
    }

    assertEquals(EventProcessor.MAX_BUFFERED_EVENTS, processor.bufferedEvents().size());
    verify(logger).error(contains("Dropped the 1 oldest events"));
    verify(logger, times(1)).error(startsWith("Dropped"));

    eventsApi.release();

    assertTrue(awaitDelivered(inFlight + EventProcessor.MAX_BUFFERED_EVENTS));
    for (String body : recorder.bodies()) {
      assertTrue(MapperFactory.getMapper().readTree(body).get("events").size() <= 1000);
    }
    String bodies = String.join("", recorder.bodies());
    assertFalse(bodies.contains("overflow-499-"));
    assertTrue(bodies.contains("overflow-500-"));
  }

  @Test
  @SneakyThrows
  public void trackEvent_dropsTheOldestKeptEventOnceTheBufferIsFull() {
    EventProcessor processor = newProcessor(1000, 0, failingWith(503, 503, 503));
    mockLogger(processor);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    for (int i = 1; i <= EventProcessor.MAX_BUFFERED_EVENTS; i++) {
      processor.trackEvent("event_" + i, "user-1", null, null, null);
    }
    flushAndWait(processor);
    assertEquals(EventProcessor.MAX_BUFFERED_EVENTS, processor.bufferedEvents().size());

    processor.trackEvent("event_1001", "user-1", null, null, null);
    flushAndWait(processor);

    assertEquals(EventProcessor.MAX_ATTEMPTS + 1, recorder.count());
    JsonNode events = MapperFactory.getMapper()
        .readTree(recorder.bodies().get(EventProcessor.MAX_ATTEMPTS)).get("events");
    assertEquals(EventProcessor.MAX_BUFFERED_EVENTS, events.size());
    assertEquals("event_2", events.get(0).get("event").asText());
    assertEquals("event_1001", events.get(events.size() - 1).get("event").asText());
  }

  @Test
  @SneakyThrows
  public void trackExposureEvent_buffersAnExposureAgainOnceItsCopyWasDropped() {
    AcceptingInterceptor eventsApi = AcceptingInterceptor.blocked();
    EventProcessor processor = newProcessor(1000, 0, eventsApi);
    mockLogger(processor);

    for (int i = 0; i < 1000; i++) {
      processor.trackEvent("purchase", "user-" + i, "1", null, null);
    }
    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, null);
    for (int i = 0; i < EventProcessor.MAX_BUFFERED_EVENTS; i++) {
      processor.trackEvent("purchase", "overflow-" + i, "1", null, null);
    }
    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, null);

    List<Map<String, Object>> buffered = processor.bufferedEvents();
    assertEquals(EventProcessor.MAX_BUFFERED_EVENTS, buffered.size());
    assertEquals("$flag_exposure", buffered.get(buffered.size() - 1).get("event"));
    eventsApi.release();
  }

  private static Stream<Arguments> healthyApiLoads() {
    return Stream.of(
        Arguments.of(Integer.MAX_VALUE, EventProcessor.MAX_BUFFERED_EVENTS + 1),
        Arguments.of(1, 1000));
  }

  @ParameterizedTest
  @MethodSource("healthyApiLoads")
  @SneakyThrows
  public void flush_neverDropsEventsOnAHealthyApi(int maxBufferItems, int events) {
    EventProcessor processor = newProcessor(maxBufferItems, 0, AcceptingInterceptor.open());
    FlagsmithLogger logger = mockLogger(processor);

    for (int i = 0; i < events; i++) {
      processor.trackEvent("purchase", "user-" + i, "1", null, null);
    }
    processor.flush();

    assertTrue(awaitDelivered(events));
    assertEquals(Collections.emptyList(), errorCalls(logger));
  }

  /**
   * Every error-level call on a mocked logger. Reads invocations rather than verify(...), whose
   * varargs matching silently misses calls with a different argument count.
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

  @SneakyThrows
  private boolean awaitDelivered(int expected) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
    while (deliveredEvents() < expected && System.nanoTime() < deadline) {
      Thread.sleep(10);
    }
    return deliveredEvents() == expected;
  }

  @Test
  @SneakyThrows
  public void flush_logsOnlyEventsTheApiRejects() {
    EventProcessor processor = newProcessor(1000, 0);
    FlagsmithLogger logger = mockLogger(processor);
    interceptor.addRule().post(EVENTS_ENDPOINT).times(1).respond(ACCEPTED_BODY, MEDIATYPE_JSON);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(
        "{\"accepted\": 1, \"rejected\": [{\"index\": 1, \"error\": \"event too long\"}]}",
        MEDIATYPE_JSON);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);
    assertEquals(1, recorder.count());
    assertEquals(Collections.emptyList(), errorCalls(logger));

    processor.trackEvent("purchase", "user-1", "1", null, null);
    processor.trackEvent("purchase", "user-2", "2", null, null);
    flushAndWait(processor);

    verify(logger).error(contains("rejected 1 of 2 events"));
    verify(logger).error(contains("event too long"));
  }

  /**
   * Accepts every request, optionally holding each until released. Answers itself because
   * MockInterceptor's canned bodies share one buffer and break under concurrent calls.
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
      return accepted(chain.request());
    }

    static Response accepted(Request request) {
      return new Response.Builder()
          .request(request)
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

    boolean awaitCount(int expected) throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
      while (count() < expected && System.nanoTime() < deadline) {
        Thread.sleep(10);
      }
      return count() >= expected;
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
