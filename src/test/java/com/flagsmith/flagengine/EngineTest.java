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
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class EngineTest {

  private Engine engine;
  private EnvironmentModel environmentModel;
  private ObjectMapper objectMapper;
  private final String environmentJsonFile =
      "src/test/java/com/flagsmith/flagengine/enginetestdata/" +
          "data/environment_n9fbf9h3v4fFgH3U3ngWhb.json";

  @BeforeClass
  public void init() {
    engine = new Engine();
    objectMapper = MapperFactory.getMapper();
  }

  private Object[][] engineTestData() {
    try {
      JsonNode engineTestData = objectMapper.
          readTree(new File(environmentJsonFile));

      JsonNode environmentNode = engineTestData.get("environment");
      environmentModel = EnvironmentModel.load(environmentNode, EnvironmentModel.class);

      JsonNode identitiesAndResponses = engineTestData.get("identities_and_responses");

      List<Object[]> returnValues = new ArrayList<>();

      if (identitiesAndResponses.isArray()) {
        for (JsonNode identityAndResponse : identitiesAndResponses) {
          IdentityModel identityModel =
              IdentityModel.load(identityAndResponse.get("identity"), IdentityModel.class);
          ResponseJSON expectedResponse =
              objectMapper.treeToValue(identityAndResponse.get("response"), ResponseJSON.class);

          Object[] parameterValues = new Object[] {
              identityModel,
              expectedResponse
          };

          returnValues.add(parameterValues);

        }
      }

      Object[][] returnValuesObj = returnValues.toArray(new Object[][] {});
      return returnValuesObj;

    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    return null;
  }

  @ParameterizedTest()
  @MethodSource("engineTestData")
  public void testEngine(IdentityModel identity, ResponseJSON expectedResponse) {
    List<FeatureStateModel> featureStates =
        engine.getIdentityFeatureStates(environmentModel, identity);

    List<FeatureStateModel> sortedFeatureStates = featureStates
        .stream()
        .sorted((featureState1, t1)
            -> featureState1.getFeature().getName()
            .compareTo(t1.getFeature().getName()))
        .collect(Collectors.toList());

    List<FeatureStateModel> sortedResponse = expectedResponse.getFlags()
        .stream()
        .sorted((featureState1, t1)
            -> featureState1.getFeature().getName()
            .compareTo(t1.getFeature().getName()))
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
}
