import com.flagsmith.FlagsmithClient;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.models.BaseFlag;
import com.flagsmith.models.Flags;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Steps for the Flagsmith SDK behavioural spec: https://github.com/Flagsmith/sdk-spec
 *
 * <p>Each method implements the Gherkin step in the comment above it.
 */
public class Steps {

  /** A flag in the shape the spec expects. */
  public static class SpecFlag {
    public final String feature;
    public final boolean enabled;
    public final Object value;

    SpecFlag(BaseFlag flag) {
      feature = flag.getFeatureName();
      enabled = flag.getEnabled();
      value = flag.getValue();
    }
  }

  static List<SpecFlag> asSpecFlags(Flags flags) {
    return flags.getAllFlags().stream().map(SpecFlag::new).collect(Collectors.toList());
  }

  // Create the SDK client
  static FlagsmithClient createClient(String environmentKey, String apiUrl) {
    return FlagsmithClient.newBuilder().setApiKey(environmentKey).withApiUrl(apiUrl).build();
  }

  // Create the SDK client with local evaluation
  static FlagsmithClient createClientWithLocalEvaluation(String environmentKey, String apiUrl) {
    return FlagsmithClient.newBuilder()
        .setApiKey(environmentKey)
        .withConfiguration(FlagsmithConfig.newBuilder()
            .baseUri(apiUrl)
            .withLocalEvaluation(true)
            .build())
        .build();
  }

  // When the application gets the environment flags
  static List<SpecFlag> getEnvironmentFlags(FlagsmithClient flagsmith)
      throws FlagsmithClientError {
    return asSpecFlags(flagsmith.getEnvironmentFlags());
  }

  // When the application gets the flags for identity "{identifier}" with traits:
  static List<SpecFlag> getIdentityFlags(
      FlagsmithClient flagsmith, String identifier, Map<String, Object> traits)
      throws FlagsmithClientError {
    return asSpecFlags(flagsmith.getIdentityFlags(identifier, traits));
  }
}
