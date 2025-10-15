package com.flagsmith.mappers;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.MapperFactory;
import com.flagsmith.flagengine.EngineConstants;
import com.flagsmith.flagengine.EnvironmentContext;
import com.flagsmith.flagengine.EvaluationContext;
import com.flagsmith.flagengine.FeatureContext;
import com.flagsmith.flagengine.FeatureValue;
import com.flagsmith.flagengine.IdentityContext;
import com.flagsmith.flagengine.SegmentCondition;
import com.flagsmith.flagengine.SegmentContext;
import com.flagsmith.flagengine.SegmentRule;
import com.flagsmith.flagengine.Segments;
import com.flagsmith.flagengine.Traits;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import com.flagsmith.models.SegmentMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EngineMappers
 *
 * <p>Utility class for mapping JSON data to flag engine context objects.
 */
public class EngineMappers {
  /**
   * Maps context and identity data to evaluation context.
   *
   * @param context    the base evaluation context
   * @param identifier the identity identifier
   * @param traits     optional traits mapping
   * @return the updated evaluation context with identity information
   */
  public static EvaluationContext mapContextAndIdentityDataToContext(
      EvaluationContext context,
      String identifier,
      Map<String, Object> traits) {

    // Create identity context
    IdentityContext identityContext = new IdentityContext()
        .withIdentifier(identifier)
        .withKey(context.getEnvironment().getKey() + "_" + identifier)
        .withTraits(new Traits());

    // Map traits if provided
    if (traits != null && !traits.isEmpty()) {
      for (Map.Entry<String, Object> entry : traits.entrySet()) {
        Object traitValue = entry.getValue();
        // Handle TraitConfig-like objects (maps with "value" key)
        if (traitValue instanceof Map) {
          Map<?, ?> traitMap = (Map<?, ?>) traitValue;
          if (traitMap.containsKey("value")) {
            traitValue = traitMap.get("value");
          }
        }
        identityContext.getTraits().setAdditionalProperty(entry.getKey(), traitValue);
      }
    }

    // Create new evaluation context with identity
    return new EvaluationContext(context)
        .withIdentity(identityContext);
  }

  /**
   * Maps environment document to evaluation context.
   *
   * @param environmentDocument the environment document JSON
   * @return the evaluation context
   */
  public static EvaluationContext mapEnvironmentDocumentToContext(
      JsonNode environmentDocument) {

    // Create environment context
    final EnvironmentContext environmentContext = new EnvironmentContext()
        .withKey(environmentDocument.get("api_key").require().asText())
        .withName(environmentDocument.get("name").require().asText());

    // Map features
    Map<String, FeatureContext> features = new HashMap<>();
    JsonNode featureStates = environmentDocument.get("feature_states");
    if (featureStates != null && featureStates.isArray()) {
      for (JsonNode featureState : featureStates) {
        FeatureContext featureContext = mapFeatureStateToFeatureContext(featureState);
        features.put(featureContext.getName(), featureContext);
      }
    }

    // Map segments
    Map<String, SegmentContext> segments = new HashMap<>();

    // Map project segments
    JsonNode project = environmentDocument.get("project");
    if (project != null) {
      JsonNode projectSegments = project.get("segments");
      if (projectSegments != null && projectSegments.isArray()) {
        for (JsonNode segment : projectSegments) {
          String segmentKey = segment.get("id").asText();
          SegmentContext segmentContext = mapSegmentToSegmentContext(segment);
          segments.put(segmentKey, segmentContext);
        }
      }
    }

    // Map identity overrides
    JsonNode identityOverrides = environmentDocument.get("identity_overrides");
    if (identityOverrides != null && identityOverrides.isArray()) {
      Map<String, SegmentContext> identityOverrideSegments = mapIdentityOverridesToSegments(
          identityOverrides);
      segments.putAll(identityOverrideSegments);
    }

    // Create evaluation context
    EvaluationContext evaluationContext = new EvaluationContext()
        .withEnvironment(environmentContext);

    // Add features individually
    com.flagsmith.flagengine.Features featuresObj = new com.flagsmith.flagengine.Features();
    for (Map.Entry<String, FeatureContext> entry : features.entrySet()) {
      featuresObj.withAdditionalProperty(entry.getKey(), entry.getValue());
    }
    evaluationContext.withFeatures(featuresObj);

    // Add segments individually
    Segments segmentsObj = new Segments();
    for (Map.Entry<String, SegmentContext> entry : segments.entrySet()) {
      segmentsObj.withAdditionalProperty(entry.getKey(), entry.getValue());
    }
    evaluationContext.withSegments(segmentsObj);

    return evaluationContext;
  }

  /**
   * Maps identity overrides to segment contexts.
   *
   * @param identityOverrides the identity overrides JSON array
   * @return map of segment contexts
   */
  private static Map<String, SegmentContext> mapIdentityOverridesToSegments(
      JsonNode identityOverrides) {

    // Map from sorted list of feature contexts to identifiers
    Map<List<FeatureContext>, List<String>> featuresToIdentifiers = new HashMap<>();

    for (JsonNode identityOverride : identityOverrides) {
      JsonNode identityFeatures = identityOverride.get("identity_features");
      if (identityFeatures == null || !identityFeatures.isArray() || identityFeatures.isEmpty()) {
        continue;
      }

      // Create overrides key as a sorted list of FeatureContext objects
      List<FeatureContext> overridesKey = new ArrayList<>();
      List<JsonNode> sortedFeatures = new ArrayList<>();
      identityFeatures.forEach(sortedFeatures::add);
      sortedFeatures.sort((a, b) -> a.get("feature").get("name").asText()
          .compareTo(b.get("feature").get("name").asText()));

      for (JsonNode featureState : sortedFeatures) {
        JsonNode feature = featureState.get("feature");
        FeatureContext featureContext = new FeatureContext()
            .withKey("")
            .withFeatureKey(feature.get("id").asText())
            .withName(feature.get("name").asText())
            .withEnabled(featureState.get("enabled").asBoolean())
            .withValue(getFeatureStateValue(featureState, "feature_state_value"))
            .withPriority(EngineConstants.STRONGEST_PRIORITY);
        overridesKey.add(featureContext);
      }

      String identifier = identityOverride.get("identifier").asText();
      featuresToIdentifiers.computeIfAbsent(overridesKey, k -> new ArrayList<>()).add(identifier);
    }

    Map<String, SegmentContext> segmentContexts = new HashMap<>();
    for (Map.Entry<List<FeatureContext>, List<String>> entry : featuresToIdentifiers.entrySet()) {
      List<FeatureContext> overridesKey = entry.getKey();
      List<String> identifiers = entry.getValue();

      String segmentKey = getVirtualSegmentKey(overridesKey);

      // Create segment condition for identifier check
      SegmentCondition identifierCondition = new SegmentCondition()
          .withProperty("$.identity.identifier")
          .withOperator(SegmentConditions.IN)
          .withValue(identifiers);

      // Create segment rule
      SegmentRule segmentRule = new SegmentRule()
          .withType(SegmentRule.Type.ALL)
          .withConditions(List.of(identifierCondition));

      // Create overrides from FeatureContext objects
      List<FeatureContext> overrides = new ArrayList<>();
      for (FeatureContext featureContext : overridesKey) {
        // Copy the feature context for the override
        FeatureContext override = new FeatureContext(featureContext)
            .withKey(""); // Identity overrides never carry multivariate options
        overrides.add(override);
      }

      SegmentMetadata metadata = new SegmentMetadata();
      metadata.setSource(SegmentMetadata.Source.IDENTITY_OVERRIDES);

      Map<String, Object> metadataMap = MapperFactory.getMapper()
          .convertValue(metadata, 
              new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
              });

      SegmentContext segmentContext = new SegmentContext()
          .withKey("") // Identity override segments never use % Split operator
          .withName("identity_overrides")
          .withRules(List.of(segmentRule))
          .withOverrides(overrides)
          .withMetadata(metadataMap);

      segmentContexts.put(segmentKey, segmentContext);
    }

    return segmentContexts;
  }

  /**
   * Maps environment document rules to context rules.
   *
   * @param rules the rules JSON array
   * @return list of segment rules
   */
  private static List<SegmentRule> mapEnvironmentDocumentRulesToContextRules(
      JsonNode rules) {

    List<SegmentRule> segmentRules = new ArrayList<>();

    for (JsonNode rule : rules) {
      // Map conditions
      List<SegmentCondition> conditions = new ArrayList<>();
      JsonNode ruleConditions = rule.get("conditions");
      if (ruleConditions != null && ruleConditions.isArray()) {
        for (JsonNode condition : ruleConditions) {
          SegmentCondition segmentCondition = new SegmentCondition()
              .withProperty(condition.get("property_").asText())
              .withOperator(SegmentConditions.valueOf(condition.get("operator").asText()))
              .withValue(condition.get("value").asText());
          conditions.add(segmentCondition);
        }
      }

      // Map sub-rules recursively
      List<SegmentRule> subRules = new ArrayList<>();
      JsonNode ruleRules = rule.get("rules");
      if (ruleRules != null && ruleRules.isArray()) {
        subRules = mapEnvironmentDocumentRulesToContextRules(ruleRules);
      }

      SegmentRule segmentRule = new SegmentRule()
          .withType(SegmentRule.Type.valueOf(rule.get("type").asText()))
          .withConditions(conditions)
          .withRules(subRules);

      segmentRules.add(segmentRule);
    }

    return segmentRules;
  }

  /**
   * Maps environment document feature states to feature contexts.
   *
   * @param featureStates the feature states JSON array
   * @return list of feature contexts
   */
  private static List<FeatureContext> mapEnvironmentDocumentFeatureStatesToFeatureContexts(
      JsonNode featureStates) {

    List<FeatureContext> featureContexts = new ArrayList<>();

    for (JsonNode featureState : featureStates) {
      FeatureContext featureContext = mapFeatureStateToFeatureContext(featureState);
      featureContexts.add(featureContext);
    }

    return featureContexts;
  }

  /**
   * Gets the feature state key from either django_id or featurestate_uuid.
   *
   * @param featureState the feature state JSON
   * @return the feature state key as string
   */
  private static String getFeatureStateKey(JsonNode featureState) {
    JsonNode node = featureState.get("django_id");
    if (node != null && !node.isNull()) {
      return node.asText();
    }
    node = featureState.get("featurestate_uuid");
    if (node != null && !node.isNull()) {
      return node.asText();
    }

    throw new IllegalArgumentException(
        "Feature state must have either 'django_id' or 'featurestate_uuid'");
  }

  private static double getMultivariateFeatureValuePriority(JsonNode multivariateValue) {
    JsonNode idNode = multivariateValue.get("id");
    if (idNode != null && !idNode.isNull()) {
      return idNode.asDouble();
    }
    // Fallback to mv_fs_value_uuid if id is not present
    JsonNode mvFsValueUuidNode = multivariateValue.get("mv_fs_value_uuid");
    if (mvFsValueUuidNode != null && !mvFsValueUuidNode.isNull()) {
      UUID mvFsValueUuid = UUID.fromString(mvFsValueUuidNode.asText());
      return mvFsValueUuid.getMostSignificantBits() & Long.MAX_VALUE;
    }

    throw new IllegalArgumentException(
        "Multivariate feature value must have either 'id' or 'mv_fs_value_uuid'");
  }

  private static Object getFeatureStateValue(JsonNode featureState, String fieldName) {
    JsonNode valueNode = featureState.get(fieldName);
    if (valueNode.isTextual() || valueNode.isLong()) {
      return valueNode.asText();
    } else if (valueNode.isNumber()) {
      if (valueNode.isInt()) {
        return valueNode.asInt();
      } else {
        return valueNode.asDouble();
      }
    } else if (valueNode.isBoolean()) {
      return valueNode.asBoolean();
    } else if (valueNode.isArray() || valueNode.isObject()) {
      return valueNode;
    }
    return null;
  }

  /**
   * Maps a single feature state to feature context.
   *
   * @param featureState the feature state JSON
   * @return the feature context
   */
  private static FeatureContext mapFeatureStateToFeatureContext(JsonNode featureState) {
    JsonNode feature = featureState.get("feature");

    FeatureContext featureContext = new FeatureContext()
        .withKey(getFeatureStateKey(featureState))
        .withFeatureKey(feature.get("id").asText())
        .withName(feature.get("name").asText())
        .withEnabled(featureState.get("enabled").asBoolean())
        .withValue(getFeatureStateValue(featureState, "feature_state_value"));

    // Handle multivariate feature state values
    JsonNode multivariateValues = featureState.get("multivariate_feature_state_values");
    if (multivariateValues != null && multivariateValues.isArray()) {
      List<FeatureValue> variants = new ArrayList<>();
      for (JsonNode multivariateValue : multivariateValues) {
        FeatureValue variant = new FeatureValue()
            .withValue(getFeatureStateValue(
                multivariateValue.get("multivariate_feature_option"), "value"))
            .withWeight(multivariateValue.get("percentage_allocation").asDouble())
            .withPriority(getMultivariateFeatureValuePriority(multivariateValue));
        variants.add(variant);
      }
      featureContext.withVariants(variants);
    }

    // Handle priority from feature segment
    JsonNode featureSegment = featureState.get("feature_segment");
    if (featureSegment != null && !featureSegment.isNull()) {
      JsonNode priority = featureSegment.get("priority");
      if (priority != null && !priority.isNull()) {
        featureContext.withPriority(priority.asDouble());
      }
    }

    return featureContext;
  }

  /**
   * Maps a segment to segment context.
   *
   * @param segment the segment JSON
   * @return the segment context
   */
  private static SegmentContext mapSegmentToSegmentContext(JsonNode segment) {
    // Map rules
    List<SegmentRule> rules = new ArrayList<>();
    JsonNode segmentRules = segment.get("rules");
    if (segmentRules != null && segmentRules.isArray()) {
      rules = mapEnvironmentDocumentRulesToContextRules(segmentRules);
    }

    // Map overrides
    List<FeatureContext> overrides = new ArrayList<>();
    JsonNode segmentFeatureStates = segment.get("feature_states");
    if (segmentFeatureStates != null && segmentFeatureStates.isArray()) {
      overrides = mapEnvironmentDocumentFeatureStatesToFeatureContexts(segmentFeatureStates);
    }

    // Map metadata
    SegmentMetadata metadata = new SegmentMetadata();
    metadata.setSource(SegmentMetadata.Source.API);
    metadata.setFlagsmithId(segment.get("id").asInt());

    Map<String, Object> metadataMap = MapperFactory.getMapper()
        .convertValue(metadata, 
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });

    String segmentKey = segment.get("id").asText();
    return new SegmentContext()
        .withKey(segmentKey)
        .withName(segment.get("name").asText())
        .withRules(rules)
        .withOverrides(overrides)
        .withMetadata(metadataMap);
  }

  /**
   * Generates a unique segment key based on feature contexts.
   * Uses a combination of feature names and values to ensure
   * uniqueness.
   *
   * @param featureContexts list of feature contexts
   * @return unique segment key
   */
  private static String getVirtualSegmentKey(
      List<FeatureContext> featureContexts) {
    StringBuilder keyBuilder = new StringBuilder();

    // Add feature information to the key
    for (FeatureContext featureContext : featureContexts) {
      keyBuilder.append(featureContext.getName())
          .append(":")
          .append(featureContext.getEnabled())
          .append(":")
          .append(featureContext.getValue())
          .append("|");
    }

    // Generate a hash of the combined string for a shorter key
    // This is safer than using List.hashCode() as we control the string content
    return String.valueOf(keyBuilder.toString().hashCode());
  }
}