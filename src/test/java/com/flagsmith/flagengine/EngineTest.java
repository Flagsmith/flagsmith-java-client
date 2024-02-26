package com.flagsmith.flagengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.MapperFactory;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.models.ResponseJSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.BeforeClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class EngineTest {
  private static final String ENVIRONMENT_JSON_FILE_LOCATION =
      "src/test/java/com/flagsmith/flagengine/enginetestdata/" +
          "data/environment_n9fbf9h3v4fFgH3U3ngWhb.json";

  private static Stream<Arguments> engineTestData() {
    try {
      ObjectMapper objectMapper = MapperFactory.getMapper();
      JsonNode engineTestData = objectMapper.
          readTree(new File(ENVIRONMENT_JSON_FILE_LOCATION));

      JsonNode environmentNode = engineTestData.get("environment");
      EnvironmentModel environmentModel = EnvironmentModel.load(environmentNode, EnvironmentModel.class);

      JsonNode identitiesAndResponses = engineTestData.get("identities_and_responses");

      List<Arguments> returnValues = new ArrayList<>();

      if (identitiesAndResponses.isArray()) {
        for (JsonNode identityAndResponse : identitiesAndResponses) {
          IdentityModel identityModel =
              IdentityModel.load(identityAndResponse.get("identity"), IdentityModel.class);
          ResponseJSON expectedResponse =
              objectMapper.treeToValue(identityAndResponse.get("response"), ResponseJSON.class);

          returnValues.add(Arguments.of(identityModel, environmentModel, expectedResponse));

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
  public void testIdentityFeatureStates(IdentityModel identity, EnvironmentModel environmentModel, ResponseJSON expectedResponse) {
    List<FeatureStateModel> featureStates =
        Engine.getIdentityFeatureStates(environmentModel, identity);

    List<FeatureStateModel> sortedFeatureStates = featureStates
        .stream()
        .sorted(Comparator.comparing(featureState -> featureState.getFeature().getName()))
        .collect(Collectors.toList());

    List<FeatureStateModel> sortedResponse = expectedResponse.getFlags()
        .stream()
        .sorted(Comparator.comparing(featureState -> featureState.getFeature().getName()))
        .collect(Collectors.toList());

    assert (sortedResponse.size() == sortedFeatureStates.size());

    int index = 0;
    for (FeatureStateModel featureState : sortedFeatureStates) {
      Object featureStateValue = featureState.getValue(identity.getDjangoId());
      Object expectedResponseValue = sortedResponse.get(index).getValue(identity.getDjangoId());

      assertEquals(featureStateValue, expectedResponseValue);
      assertEquals(featureState.getEnabled(), sortedResponse.get(index).getEnabled());
      index++;
    }
  }

  @ParameterizedTest()
  @MethodSource("engineTestData")
  public void getIdentityFeatureStateForFlag(IdentityModel identity, EnvironmentModel environmentModel, ResponseJSON expectedResponse) {
    environmentModel.initializeCache();

    for (FeatureStateModel expectedFlag : expectedResponse.getFlags()) {
      FeatureStateModel calculatedFlag =
          Engine.getIdentityFeatureStateForFlag(environmentModel, identity, expectedFlag.getFeature().getName());

      Object calculatedFeatureValue = calculatedFlag.getValue(identity.getDjangoId());
      Object expectedFeatureValue = expectedFlag.getValue(identity.getDjangoId());

      assertEquals(calculatedFeatureValue, expectedFeatureValue);
      assertEquals(calculatedFlag.getEnabled(), expectedFlag.getEnabled());
    }
  }
}
