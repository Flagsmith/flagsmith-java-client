package com.flagsmith.flagengine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.MapperFactory;
import com.flagsmith.mappers.EngineMappers;
import com.flagsmith.models.FeatureStateModel;
import com.flagsmith.models.Flags;

import groovy.util.Eval;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.BeforeClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EngineTest {
  private static final String ENVIRONMENT_JSON_FILE_LOCATION = "src/test/java/com/flagsmith/flagengine/enginetestdata/"
      +
      "data/environment_n9fbf9h3v4fFgH3U3ngWhb.json";
  private static ObjectMapper objectMapper = MapperFactory.getMapper();

  private static Stream<Arguments> engineTestData() {
    try {
      JsonNode engineTestData = objectMapper.readTree(new File(ENVIRONMENT_JSON_FILE_LOCATION));

      JsonNode environmentDocument = engineTestData.get("environment");
      JsonNode identitiesAndResponses = engineTestData.get("identities_and_responses");

      EvaluationContext baseEvaluationContext = EngineMappers
          .mapEnvironmentDocumentToContext(environmentDocument);

      List<Arguments> returnValues = new ArrayList<>();

      if (identitiesAndResponses.isArray()) {
        for (JsonNode identityAndResponse : identitiesAndResponses) {
          JsonNode identity = identityAndResponse.get("identity");
          Map<String, Object> traits = Optional.ofNullable(identity.get("identity_traits"))
              .filter(JsonNode::isArray)
              .map(node -> StreamSupport.stream(node.spliterator(), false)
                  .filter(trait -> trait.hasNonNull("trait_key"))
                  .collect(Collectors.toMap(
                      trait -> trait.get("trait_key").asText(),
                      trait -> objectMapper.convertValue(trait.get("trait_value"), Object.class))))
              .orElseGet(HashMap::new);

          EvaluationContext evaluationContext = EngineMappers.mapContextAndIdentityDataToContext(
              baseEvaluationContext, identity.get("identifier").asText(), traits);

          JsonNode expectedResponse = identityAndResponse.get("response");

          returnValues.add(Arguments.of(evaluationContext, expectedResponse));

        }
      }

      return returnValues.stream();

    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    return null;
  }

  @ParameterizedTest()
  @MethodSource("engineTestData")
  public void testEngine(EvaluationContext evaluationContext, JsonNode expectedResponse) {
    EvaluationResult evaluationResult = Engine.getEvaluationResult(evaluationContext);

    List<FeatureStateModel> flags = objectMapper.convertValue(
        expectedResponse.get("flags"),
        new TypeReference<List<FeatureStateModel>>() {
        });

    flags.sort((fsm1, fsm2) -> fsm1.getFeature().getName().compareTo(fsm2.getFeature().getName()));
    List<FlagResult> sortedResults = evaluationResult.getFlags().stream()
        .sorted((fr1, fr2) -> fr1.getName().compareTo(fr2.getName()))
        .collect(Collectors.toList());

    assertEquals(flags.size(), sortedResults.size());
    for (int i = 0; i < flags.size(); i++) {
      FeatureStateModel fsm = flags.get(i);
      FlagResult fr = sortedResults.get(i);

      assertEquals(fr.getName(), fsm.getFeature().getName());
      assertEquals(fr.getEnabled(), fsm.getEnabled());
      assertEquals(fr.getValue(), fsm.getValue());
    }
  }
}