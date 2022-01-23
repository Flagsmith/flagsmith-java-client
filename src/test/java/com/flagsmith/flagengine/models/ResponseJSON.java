package com.flagsmith.flagengine.models;

import com.flagsmith.flagengine.data.FeatureStates;
import lombok.Data;

@Data
public class ResponseJSON {
    // TODO - this is not featureStates in essence,
    //  this is a response object

    private FeatureStates flags;
}
