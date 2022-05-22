package com.flagsmith.flagengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.MapperFactory;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.features.FlagsmithValue;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.stream.StreamSupport;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Test(groups = "unit")
public class EngineTest {

  private Engine engine;
  private EnvironmentModel environmentModel;
  private ObjectMapper objectMapper;
  private final String environmentJsonFile =
      "src/test/java/com/flagsmith/flagengine/enginetestdata/" +
          "data/environment_n9fbf9h3v4fFgH3U3ngWhb.json";

  @BeforeClass(groups = "unit")
  public void init() {
    engine = new Engine();
    objectMapper = MapperFactory.getMapper();
  }

  @DataProvider(name = "environmentdata")
  private Object[][] load() {
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

          JsonNode expectedResponse = identityAndResponse.get("response");

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

  @Test(dataProvider = "environmentdata")
  public void testEngine(IdentityModel identity, JsonNode expectedResponse) {
    List<FeatureStateModel> featureStates =
        Engine.getIdentityFeatureStates(environmentModel, identity);

    List<FeatureStateModel> sortedFeatureStates = featureStates
        .stream()
        .sorted(Comparator.comparing(featureState -> featureState.getFeature().getName()))
        .collect(Collectors.toList());

    JsonNode unsortedResponse = expectedResponse.get("flags");
    List<JsonNode> sortedResponse = StreamSupport.stream(
          unsortedResponse.spliterator(), false
        )
        .sorted(Comparator.comparing(
            featureState -> featureState.get("feature").get("name").asText()
        ))
        .collect(Collectors.toList());

    assert (sortedResponse.size() == sortedResponse.size());

    int index = 0;
    for (FeatureStateModel featureState : sortedFeatureStates) {
      FlagsmithValue featureStateValue = featureState.getValue(identity.getDjangoId());
      Object expectedResponseValue = sortedResponse.get(index).get("feature_state_value").asText();
      FlagsmithValue expectedResponseFlagsmithValue
          = FlagsmithValue.fromUntypedValue(expectedResponseValue);

      Assert.assertEquals(featureStateValue, expectedResponseFlagsmithValue);
      Assert.assertEquals(featureState.getEnabled().booleanValue(), sortedResponse.get(index).get("enabled").booleanValue());
      index++;
    }
  }
}
