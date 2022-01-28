package com.flagsmith.flagengine.features;

import com.flagsmith.flagengine.utils.models.BaseModel;
import lombok.Data;

@Data
public class MultivariateFeatureOptionModel extends BaseModel {
    private String value;
    private Integer id;
}
