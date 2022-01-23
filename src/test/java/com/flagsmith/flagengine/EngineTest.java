package com.flagsmith.flagengine;

import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.models.EngineTestCase;
import com.flagsmith.flagengine.models.ResponseJSON;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@Test(groups = "unit")
public class EngineTest {

    private Engine engine;
    private EnvironmentModel environmentModel;
    private ObjectMapper objectMapper;
    private final String environmentJsonFile = "src/test/java/com/flagsmith/flagengine/enginetestdata/data/environment_n9fbf9h3v4fFgH3U3ngWhb.json";

    @BeforeMethod(groups = "unit")
    public void init() {
        engine = new Engine();
        objectMapper = new ObjectMapper();
    }

    @DataProvider(name = "environmentdata")
    private Object[][] load() {
        try {
            JsonNode engineTestData = objectMapper.readTree(new File(environmentJsonFile));
            EngineTestCase testData = objectMapper.readValue(new File(environmentJsonFile), EngineTestCase.class);
            environmentModel = testData.getEnvironment();

            if (testData.getIdentitiesAndResponses() != null) {
                testData.getIdentitiesAndResponses().forEach((a) -> {

                });
            }
        } catch (Exception e) {

        }
        return null;
    }

    @Test(dataProvider = "environmentdata")
    public void testEngine(IdentityModel identity, ResponseJSON expectedResponse) {
        FeatureStates response = engine.getIdentityFeatureStates(environmentModel, identity);

        List<FeatureState> featureStates = response.getFeatureStates();

        List<FeatureState> sortedFeatureStates = featureStates
                .stream()
                .sorted((featureState1, t1)
                    -> featureState1.getFeature().getName()
                        .compareTo(t1.getFeature().getName()))
                .collect(Collectors.toList());

        List<FeatureState> sortedResponse = expectedResponse.getFlags().getFeatureStates()
                .stream()
                .sorted((featureState1, t1)
                        -> featureState1.getFeature().getName()
                        .compareTo(t1.getFeature().getName()))
                .collect(Collectors.toList());

        assert(sortedResponse.size() == sortedFeatureStates.size());

        int index = 0;
        for(FeatureState featureState : sortedFeatureStates) {
            // sortedResponse is not feature states but maps
            assert(featureState.getValue(identity.getDjangoId()) == sortedResponse.get(index).getValue(identity.getDjangoId()));

            assert(featureState.getEnabled() == sortedResponse.get(index).getEnabled());

            index++;
        }


    }


}
