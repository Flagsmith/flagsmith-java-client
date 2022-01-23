package com.flagsmith.flagengine.features;

import lombok.Data;

@Data
public class MultivariateFeatureStateValueModel {
    private MultivariateFeatureOptionModel multivariateFeatureOption;
    private Float percentageAllocation;
    private Integer id;
    private String mvFsValueUuid;
}
