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
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureModel;
import com.flagsmith.flagengine.features.FeatureSegmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.features.MultivariateFeatureStateValueModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.projects.ProjectModel;
import com.flagsmith.flagengine.segments.SegmentConditionModel;
import com.flagsmith.flagengine.segments.SegmentModel;
import com.flagsmith.flagengine.segments.SegmentRuleModel;
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
    return mapEnvironmentToContext(
        MapperFactory.getMapper().convertValue(environmentDocument,
            EnvironmentModel.class));
  }

  /**
   * Maps environment model to evaluation context.
   *
   * @param environmentModel the environment model
   * @return the evaluation context
   */
  public static EvaluationContext mapEnvironmentToContext(
      EnvironmentModel environmentModel) {
    // Create environment context
    final EnvironmentContext environmentContext = new EnvironmentContext()
        .withKey(environmentModel.getApiKey())
        .withName(environmentModel.getName());

    // Map features
    Map<String, FeatureContext> features = new HashMap<>();
    for (FeatureStateModel featureState : environmentModel.getFeatureStates()) {
      FeatureContext featureContext = mapFeatureStateToFeatureContext(featureState);
      features.put(featureContext.getName(), featureContext);
    }

    // Map segments
    Map<String, SegmentContext> segments = new HashMap<>();

    // Map project segments
    ProjectModel project = environmentModel.getProject();
    for (SegmentModel segment : project.getSegments()) {
      String segmentKey = String.valueOf(segment.getId());
      segments.put(segmentKey, mapSegmentToSegmentContext(segment));
    }

    // Map identity overrides
    Map<String, SegmentContext> identityOverrideSegments = mapIdentityOverridesToSegments(
        environmentModel.getIdentityOverrides());
    segments.putAll(identityOverrideSegments);

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
      List<IdentityModel> identityOverrides) {

    // Map from sorted list of feature contexts to identifiers
    Map<List<FeatureContext>, List<String>> featuresToIdentifiers = new HashMap<>();

    for (IdentityModel identityOverride : identityOverrides) {
      List<FeatureStateModel> identityFeatures = identityOverride.getIdentityFeatures();
      if (identityFeatures == null || identityFeatures.isEmpty()) {
        continue;
      }

      // Create overrides key as a sorted list of FeatureContext objects
      List<FeatureContext> overridesKey = new ArrayList<>();
      List<FeatureStateModel> sortedFeatures = new ArrayList<>();
      identityFeatures.forEach(sortedFeatures::add);
      sortedFeatures.sort((a, b) -> a.getFeature().getName()
          .compareTo(b.getFeature().getName()));

      for (FeatureStateModel featureState : sortedFeatures) {
        FeatureModel feature = featureState.getFeature();
        FeatureContext featureContext = new FeatureContext()
            .withKey("")
            .withFeatureKey(String.valueOf(feature.getId()))
            .withName(feature.getName())
            .withEnabled(featureState.getEnabled())
            .withValue(featureState.getValue())
            .withPriority(EngineConstants.STRONGEST_PRIORITY);
        overridesKey.add(featureContext);
      }

      String identifier = identityOverride.getIdentifier();
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
      List<SegmentRuleModel> rules) {

    List<SegmentRule> segmentRules = new ArrayList<>();

    for (SegmentRuleModel rule : rules) {
      // Map conditions
      List<SegmentCondition> conditions = new ArrayList<>();

      if (rule.getConditions() != null) {
        for (SegmentConditionModel condition : rule.getConditions()) {
          SegmentCondition segmentCondition = new SegmentCondition()
              .withProperty(condition.getProperty())
              .withOperator(condition.getOperator())
              .withValue(condition.getValue());
          conditions.add(segmentCondition);
        }
      }

      // Map sub-rules recursively
      List<SegmentRule> subRules = mapEnvironmentDocumentRulesToContextRules(
          rule.getRules());

      SegmentRule segmentRule = new SegmentRule()
          .withType(SegmentRule.Type.fromValue(rule.getType()))
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
      List<FeatureStateModel> featureStates) {

    List<FeatureContext> featureContexts = new ArrayList<>();

    if (featureStates != null) {
      for (FeatureStateModel featureState : featureStates) {
        FeatureContext featureContext = mapFeatureStateToFeatureContext(featureState);
        featureContexts.add(featureContext);
      }
    }

    return featureContexts;
  }

  /**
   * Gets the feature state key from either django_id or featurestate_uuid.
   *
   * @param featureState the feature state JSON
   * @return the feature state key as string
   */
  private static String getFeatureStateKey(FeatureStateModel featureState) {
    Integer djangoId = featureState.getDjangoId();
    if (djangoId != null) {
      return djangoId.toString();
    }
    return featureState.getFeaturestateUuid();
  }

  private static double getMultivariateFeatureValuePriority(
      MultivariateFeatureStateValueModel multivariateValue) {
    if (multivariateValue.getId() != null) {
      return multivariateValue.getId();
    }

    // Fallback to mv_fs_value_uuid if id is not present
    UUID mvFsValueUuid = UUID.fromString(multivariateValue.getMvFsValueUuid());
    return mvFsValueUuid.getMostSignificantBits() & Long.MAX_VALUE;
  }

  /**
   * Maps a single feature state to feature context.
   *
   * @param featureState the feature state JSON
   * @return the feature context
   */
  private static FeatureContext mapFeatureStateToFeatureContext(FeatureStateModel featureState) {
    FeatureContext featureContext = new FeatureContext()
        .withKey(getFeatureStateKey(featureState))
        .withFeatureKey(String.valueOf(featureState.getFeature().getId()))
        .withName(featureState.getFeature().getName())
        .withEnabled(featureState.getEnabled())
        .withValue(featureState.getValue());

    // Handle multivariate feature state values
    List<FeatureValue> variants = new ArrayList<>();
    for (MultivariateFeatureStateValueModel mvValue :
        featureState.getMultivariateFeatureStateValues()) {
      FeatureValue variant = new FeatureValue()
          .withValue(mvValue.getMultivariateFeatureOption().getValue())
          .withWeight(mvValue.getPercentageAllocation().doubleValue())
          .withPriority(getMultivariateFeatureValuePriority(mvValue));
      variants.add(variant);
    }
    featureContext.setVariants(variants);

    // Handle priority from feature segment
    FeatureSegmentModel featureSegment = featureState.getFeatureSegment();
    if (featureSegment != null) {
      Double priority = (double) featureSegment.getPriority();
      if (priority != null) {
        featureContext.withPriority(priority);
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
  private static SegmentContext mapSegmentToSegmentContext(SegmentModel segment) {
    // Map rules
    List<SegmentRule> rules = mapEnvironmentDocumentRulesToContextRules(
        segment.getRules());

    // Map overrides
    List<FeatureStateModel> segmentFeatureStates = segment.getFeatureStates();
    List<FeatureContext> overrides = mapEnvironmentDocumentFeatureStatesToFeatureContexts(
        segmentFeatureStates);

    // Map metadata
    SegmentMetadata metadata = new SegmentMetadata();
    metadata.setSource(SegmentMetadata.Source.API);
    metadata.setFlagsmithId(segment.getId());

    Map<String, Object> metadataMap = MapperFactory.getMapper()
          .convertValue(metadata,
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });

    String segmentKey = String.valueOf(segment.getId());
    return new SegmentContext()
        .withKey(segmentKey)
        .withName(segment.getName())
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