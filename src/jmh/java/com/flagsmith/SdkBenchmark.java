package com.flagsmith;

import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.flagengine.EvaluationContext;
import com.flagsmith.models.Segment;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Benchmarks a locally evaluating SDK client against environment documents tiered by size.
 */
@State(Scope.Benchmark)
public class SdkBenchmark {
  private static final MediaType JSON = MediaType.get("application/json");
  private static final String IDENTITY_OVERRIDES_IDENTIFIER = "identity-override-1";
  private static final String IDENTIFIER = "user-1";
  private static final Map<String, Object> TRAITS = Map.of(
      "plan", "plan-7",
      "age", 30,
      "country", "GB",
      "version", "2.5.1",
      "email", "jane@example.com",
      "user_id", 12,
      "beta", true,
      "score", 95,
      "tier", "pro",
      "device", "iPhone 15");

  @Param({"small", "medium", "large"})
  public String environment;

  private FlagsmithClient client;

  /**
   * Build a locally evaluating client serving the environment document from memory.
   */
  @Setup
  public void buildClient() throws IOException {
    byte[] environmentDocument;
    try (InputStream stream = getClass().getResourceAsStream(
        "/environments/" + environment + ".json")) {
      environmentDocument = stream.readAllBytes();
    }

    client = FlagsmithClient.newBuilder()
        .setApiKey("ser.benchmark")
        .withConfiguration(FlagsmithConfig.newBuilder()
            .baseUri("http://flagsmith.benchmark/api/v1/")
            .addHttpInterceptor(serveEnvironmentDocument(environmentDocument))
            .withLocalEvaluation(true)
            .withEnvironmentRefreshIntervalSeconds(86400) // Refreshed by benchmarks only
            .build())
        .build();
  }

  @TearDown
  public void closeClient() {
    client.close();
  }

  /**
   * Get a feature value from the environment flags.
   */
  @Benchmark
  public Object getEnvironmentFlags() throws FlagsmithClientError {
    return client.getEnvironmentFlags().getFeatureValue("feature_1");
  }

  /**
   * Check a feature for an identity with identity overrides.
   */
  @Benchmark
  public boolean getIdentityFlagsWithIdentityOverrides() throws FlagsmithClientError {
    return client.getIdentityFlags(IDENTITY_OVERRIDES_IDENTIFIER).isFeatureEnabled("feature_8");
  }

  /**
   * Get a feature value for an identity without traits.
   */
  @Benchmark
  public Object getIdentityFlagsNoTraits() throws FlagsmithClientError {
    return client.getIdentityFlags(IDENTIFIER).getFeatureValue("feature_1");
  }

  /**
   * Get a feature value for an identity with traits.
   */
  @Benchmark
  public Object getIdentityFlagsWithTraits() throws FlagsmithClientError {
    return client.getIdentityFlags(IDENTIFIER, TRAITS).getFeatureValue("feature_1");
  }

  /**
   * List the segments of an identity with traits.
   */
  @Benchmark
  public List<Segment> getIdentitySegmentsWithTraits() throws FlagsmithClientError {
    return client.getIdentitySegments(IDENTIFIER, TRAITS);
  }

  private static Interceptor serveEnvironmentDocument(byte[] environmentDocument) {
    return chain -> new Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(ResponseBody.create(environmentDocument, JSON))
        .build();
  }
}
