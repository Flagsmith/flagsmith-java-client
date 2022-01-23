package com.flagsmith.flagengine.models;

import com.flagsmith.flagengine.environments.EnvironmentModel;
import lombok.Data;

import java.util.List;

@Data
public class EngineTestCase {
    private EnvironmentModel environment;
    private List<IdentitiesAndResponses> identitiesAndResponses;
}
