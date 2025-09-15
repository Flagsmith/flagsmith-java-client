package com.flagsmith.mappers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.flagengine.EnvironmentContext;
import com.flagsmith.flagengine.EvaluationContext;
import com.flagsmith.flagengine.FeatureContext;
import com.flagsmith.flagengine.IdentityContext;
import com.flagsmith.flagengine.SegmentCondition;
import com.flagsmith.flagengine.SegmentContext;
import com.flagsmith.flagengine.SegmentRule;
import com.flagsmith.flagengine.Segments;
import com.flagsmith.flagengine.Traits;
import com.flagsmith.flagengine.segments.constants.SegmentConditions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * EngineMappers
 *
 * <p>Utility class for mapping JSON data to flag engine context objects.
 */
public class EngineMappers {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Maps context and identity data to evaluation context.
   *
   * @param context     the base evaluation context
   * @param identifier  the identity identifier
   * @param traits      optional traits mapping
   * @return the updated evaluation context with identity information
   */
  public static EvaluationContext mapContextAndIdentityDataToContext(
      EvaluationContext context,
      String identifier,
      Map<String, Object> traits) {
    
    // Create identity context
    IdentityContext identityContext = new IdentityContext()
        .withIdentifier(identifier)
        .withKey(context.getEnvironment().getKey() + "_" + identifier);
    
    // Map traits if provided
    if (traits != null && !traits.isEmpty()) {
      Traits identityTraits = new Traits();
      for (Map.Entry<String, Object> entry : traits.entrySet()) {
        Object traitValue = entry.getValue();
        // Handle TraitConfig-like objects (maps with "value" key)
        if (traitValue instanceof Map) {
          Map<?, ?> traitMap = (Map<?, ?>) traitValue;
          if (traitMap.containsKey("value")) {
            traitValue = traitMap.get("value");
          }
        }
        identityTraits.withAdditionalProperty(entry.getKey(), traitValue);
      }
      identityContext.withTraits(identityTraits);
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
        .withKey(environmentDocument.get("api_key").asText())
        .withName("Test Environment");
    
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
      Map<String, SegmentContext> identityOverrideSegments =
          mapIdentityOverridesToSegments(identityOverrides);
      segments.putAll(identityOverrideSegments);
    }
    
    // Create evaluation context
    return new EvaluationContext()
        .withEnvironment(environmentContext)
        .withFeatures(new com.flagsmith.flagengine.Features()
            .withAdditionalProperty("features", features))
        .withSegments(new Segments()
            .withAdditionalProperty("segments", segments));
  }

  /**
   * Maps identity overrides to segment contexts.
   *
   * @param identityOverrides the identity overrides JSON array
   * @return map of segment contexts
   */
  private static Map<String, SegmentContext> mapIdentityOverridesToSegments(
      JsonNode identityOverrides) {
    
    Map<List<Object>, List<String>> featuresToIdentifiers = new HashMap<>();
    
    for (JsonNode identityOverride : identityOverrides) {
      JsonNode identityFeatures = identityOverride.get("identity_features");
      if (identityFeatures == null || !identityFeatures.isArray() || identityFeatures.size() == 0) {
        continue;
      }
      
      // Create overrides key
      List<Object> overridesKey = new ArrayList<>();
      List<JsonNode> sortedFeatures = new ArrayList<>();
      identityFeatures.forEach(sortedFeatures::add);
      sortedFeatures.sort((a, b) -> a.get("feature").get("name").asText()
          .compareTo(b.get("feature").get("name").asText()));
      
      for (JsonNode featureState : sortedFeatures) {
        JsonNode feature = featureState.get("feature");
        overridesKey.add(feature.get("id").asText());
        overridesKey.add(feature.get("name").asText());
        overridesKey.add(featureState.get("enabled").asBoolean());
        overridesKey.add(featureState.get("feature_state_value"));
      }
      
      String identifier = identityOverride.get("identifier").asText();
      featuresToIdentifiers.computeIfAbsent(overridesKey, k -> new ArrayList<>()).add(identifier);
    }
    
    Map<String, SegmentContext> segmentContexts = new HashMap<>();
    for (Map.Entry<List<Object>, List<String>> entry : featuresToIdentifiers.entrySet()) {
      List<Object> overridesKey = entry.getKey();
      List<String> identifiers = entry.getValue();
      
      // Generate unique segment key
      String segmentKey = String.valueOf(overridesKey.hashCode());
      
      // Create segment condition for identifier check
      SegmentCondition identifierCondition = new SegmentCondition()
          .withProperty("$.identity.identifier")
          .withOperator(SegmentConditions.IN)
          .withValue(identifiers);
      
      // Create segment rule
      SegmentRule segmentRule = new SegmentRule()
          .withType(SegmentRule.Type.ALL)
          .withConditions(List.of(identifierCondition));
      
      // Create overrides
      List<FeatureContext> overrides = new ArrayList<>();
      for (int i = 0; i < overridesKey.size(); i += 4) {
        String featureKey = overridesKey.get(i).toString();
        String featureName = overridesKey.get(i + 1).toString();
        boolean featureEnabled = (Boolean) overridesKey.get(i + 2);
        Object featureValue = overridesKey.get(i + 3);
        
        FeatureContext override = new FeatureContext()
            .withKey("")
            .withFeatureKey(featureKey)
            .withName(featureName)
            .withEnabled(featureEnabled)
            .withValue(featureValue)
            .withPriority(Double.NEGATIVE_INFINITY);
        
        overrides.add(override);
      }
      
      SegmentContext segmentContext = new SegmentContext()
          .withKey("")
          .withName("identity_overrides")
          .withRules(List.of(segmentRule))
          .withOverrides(overrides);
      
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
              .withValue(condition.get("value"));
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
   * Maps a single feature state to feature context.
   *
   * @param featureState the feature state JSON
   * @return the feature context
   */
  private static FeatureContext mapFeatureStateToFeatureContext(JsonNode featureState) {
    JsonNode feature = featureState.get("feature");
    
    FeatureContext featureContext = new FeatureContext()
        .withKey(featureState.get("id").asText())
        .withFeatureKey(feature.get("id").asText())
        .withName(feature.get("name").asText())
        .withEnabled(featureState.get("enabled").asBoolean())
        .withValue(featureState.get("feature_state_value"));
    
    // Handle multivariate feature state values
    JsonNode multivariateValues = featureState.get("multivariate_feature_state_values");
    if (multivariateValues != null && multivariateValues.isArray()) {
      List<Map<String, Object>> variants = new ArrayList<>();
      for (JsonNode multivariateValue : multivariateValues) {
        Map<String, Object> variant = new HashMap<>();
        variant.put("value", multivariateValue.get("multivariate_feature_option").get("value"));
        variant.put("weight", multivariateValue.get("percentage_allocation").asDouble());
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
    String segmentKey = segment.get("id").asText();
    
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
    
    return new SegmentContext()
        .withKey(segmentKey)
        .withName(segment.get("name").asText())
        .withRules(rules)
        .withOverrides(overrides);
  }
}