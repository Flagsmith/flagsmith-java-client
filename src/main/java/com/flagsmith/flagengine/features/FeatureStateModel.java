package com.flagsmith.flagengine.features;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.utils.models.BaseModel;
import lombok.Data;

import java.util.List;

@Data
public class FeatureStateModel extends BaseModel {
    private FeatureModel feature;
    private Boolean enabled;
    @JsonProperty("django_id")
    private Integer djangoId;
    @JsonProperty("featurestate_uuid")
    private String featurestateUuid;
    @JsonProperty("multivariate_feature_state_values")
    private List<MultivariateFeatureStateValueModel> multivariateFeatureStateValues;
    @JsonProperty("feature_state_value")
    private Object value;

    public Object getValue(Integer identityId) {

        if (identityId != null && multivariateFeatureStateValues.size() > 0) {

        }

        return value;
    }

    private Object getMultiVariateValue(Integer identityId) {
        return null;
    }

}
