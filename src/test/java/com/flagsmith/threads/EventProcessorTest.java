package com.flagsmith.threads;

import static okhttp3.mock.MediaTypes.MEDIATYPE_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.FlagsmithLogger;
import com.flagsmith.MapperFactory;
import com.flagsmith.interfaces.FlagsmithSdk;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mock.MockInterceptor;
import okio.Buffer;
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
    OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(recorder)
        .addInterceptor(interceptor)
        .build();
    eventProcessor = new EventProcessor(
        HttpUrl.get(EVENTS_URI),
        maxBufferItems,
        flushIntervalMillis,
        3000,
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

    assertEquals(1, processor.getBuffer().size());

    Map<String, Object> event = processor.getBuffer().get(0);
    assertEquals(
        Arrays.asList("event", "feature_name", "identifier", "value", "traits", "metadata",
            "timestamp"),
        new ArrayList<>(event.keySet()));
    assertEquals("purchase", event.get("event"));
    assertNull(event.get("feature_name"));
    assertEquals("user-123", event.get("identifier"));
    assertEquals("49.0", event.get("value"));
    assertEquals(traits, event.get("traits"));

    @SuppressWarnings("unchecked")
    Map<String, Object> eventMetadata = (Map<String, Object>) event.get("metadata");
    assertEquals("checkout", eventMetadata.get("source"));
    assertNotNull(eventMetadata.get("sdk_version"));

    long timestamp = (Long) event.get("timestamp");
    assertTrue(timestamp >= before);
  }

  @Test
  public void trackEvent_buffersNullValueAsNull() {
    EventProcessor processor = newProcessor(1000, 0);

    processor.trackEvent("purchase", "user-123", null, null, null);

    Map<String, Object> event = processor.getBuffer().get(0);
    assertNull(event.get("value"));
    assertNull(event.get("traits"));
  }

  @Test
  public void trackExposureEvent_dedupesIdenticalExposuresWithinTheFlushWindow() {
    EventProcessor processor = newProcessor(1000, 0);
    Map<String, Object> metadata = Collections.singletonMap("experiment_id", 42);

    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, metadata);
    processor.trackExposureEvent("checkout_cta", "user-1", "treatment", null, metadata);

    assertEquals(1, processor.getBuffer().size());
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

    assertEquals(5, processor.getBuffer().size());
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

    assertEquals(1, processor.getBuffer().size());
    assertEquals(1, recorder.count());
  }

  @Test
  public void trackEvent_neverDedupesCustomEvents() {
    EventProcessor processor = newProcessor(1000, 0);

    processor.trackEvent("purchase", "user-1", "49.00", null, null);
    processor.trackEvent("purchase", "user-1", "49.00", null, null);

    assertEquals(2, processor.getBuffer().size());
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
    assertTrue(processor.getBuffer().isEmpty());
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
    assertEquals(1, processor.getBuffer().size());

    processor.trackEvent("purchase", "user-2", "2", null, null);
    assertTrue(processor.getBuffer().isEmpty());

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
    assertTrue(processor.getBuffer().isEmpty());
  }

  @Test
  @SneakyThrows
  public void flush_doesNotRetryOnClientError() {
    EventProcessor processor = newProcessor(1000, 0);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(400);

    processor.trackEvent("purchase", "user-1", "1", null, null);
    flushAndWait(processor);

    assertEquals(1, recorder.count());
    assertTrue(processor.getBuffer().isEmpty());
  }

  @Test
  @SneakyThrows
  public void start_flushesOnTheTimer() {
    EventProcessor processor = newProcessor(1000, 500);
    interceptor.addRule().post(EVENTS_ENDPOINT).anyTimes().respond(ACCEPTED_BODY, MEDIATYPE_JSON);

    processor.start();
    processor.trackEvent("purchase", "user-1", "1", null, null);
    Thread.sleep(800);

    assertEquals(1, recorder.count());
    assertTrue(processor.getBuffer().isEmpty());
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
  public void trackEvent_neverThrowsWhenTheApiIsMissing() {
    EventProcessor processor = newProcessor(1, 0);
    processor.setApi(null);

    processor.trackEvent("purchase", "user-1", "1", null, null);

    assertEquals(0, recorder.count());
    assertTrue(processor.getBuffer().isEmpty());
  }

  /** Records every request that reaches the network, with its body. */
  private static class RecordingInterceptor implements Interceptor {

    private final List<String> bodies = Collections.synchronizedList(new ArrayList<>());

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request request = chain.request();
      Buffer buffer = new Buffer();
      if (request.body() != null) {
        request.body().writeTo(buffer);
      }
      bodies.add(buffer.readUtf8());
      return chain.proceed(request);
    }

    int count() {
      return bodies.size();
    }

    List<String> bodies() {
      return new ArrayList<>(bodies);
    }
  }
}
