package com.flagsmith.flagengine.features;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.utils.Hashing;
import com.flagsmith.flagengine.utils.models.BaseModel;
import lombok.Data;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

        if (identityId != null && multivariateFeatureStateValues != null && multivariateFeatureStateValues.size() > 0) {
            return getMultiVariateValue(identityId);
        }

        return value;
    }

    private Object getMultiVariateValue(Integer identityId) {

        List<String> objectIds = Arrays.asList(
                (djangoId != null && djangoId != 0 ? djangoId.toString() : featurestateUuid),
                identityId.toString()
        );

        Float percentageValue = Hashing.getHashedPercentageForObjectIds(objectIds);
        Float startPercentage = 0f;

        List<MultivariateFeatureStateValueModel> sortedMultiVariateFeatureStates = multivariateFeatureStateValues
                .stream()
                .sorted((smvfs1, smvfs2) -> smvfs1.getSortValue().compareTo(smvfs2.getSortValue()))
                .collect(Collectors.toList());

        for (MultivariateFeatureStateValueModel multiVariate: sortedMultiVariateFeatureStates) {
            Float limit = multiVariate.getPercentageAllocation() + startPercentage;

            if (startPercentage <= percentageValue && percentageValue < limit) {
                return multiVariate.getMultivariateFeatureOption().getValue();
            }

            startPercentage = limit;
        }

        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FeatureStateModel)) {
            return false;
        }

        return this.getFeature().getId() == ((FeatureStateModel) o).getFeature().getId();

    }
}
;