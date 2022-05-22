package com.flagsmith.flagengine.features;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flagsmith.flagengine.utils.Hashing;
import com.flagsmith.utils.models.BaseModel;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class FeatureStateModel extends BaseModel {
  private FeatureModel feature;
  private Boolean enabled;
  @JsonProperty("django_id")
  private Integer djangoId;
  @JsonProperty("featurestate_uuid")
  private String featurestateUuid = UUID.randomUUID().toString();
  @JsonProperty("multivariate_feature_state_values")
  private List<MultivariateFeatureStateValueModel> multivariateFeatureStateValues;
  @JsonProperty("feature_state_value")
  private FlagsmithValue value;
  @JsonProperty("feature_segment")
  private FeatureSegmentModel featureSegment;

  /**
   * Set the value.
   * 
   * @param value untype object value.
   */
  public void setValue(Object value) {
    this.value = FlagsmithValue.fromUntypedValue(value);
  }

  /**
   * Returns the value object.
   * 
   * @param identityId Identity ID
   * @return
   */
  public FlagsmithValue getValue(Object identityId) {

    if (identityId != null && multivariateFeatureStateValues != null
        && multivariateFeatureStateValues.size() > 0) {
      return getMultiVariateValue(identityId);
    }

    return value;
  }

  /**
   * Determines the multi variate value.
   * 
   * @param identityId Identity ID
   * @return
   */
  private FlagsmithValue getMultiVariateValue(Object identityId) {

    List<String> objectIds = Arrays.asList(
        (djangoId != null && djangoId != 0 ? djangoId.toString() : featurestateUuid),
        identityId.toString());

    Float percentageValue = Hashing.getInstance().getHashedPercentageForObjectIds(objectIds);
    Float startPercentage = 0f;

    List<MultivariateFeatureStateValueModel> sortedMultiVariateFeatureStates = multivariateFeatureStateValues
        .stream()
        .sorted((smvfs1, smvfs2) -> smvfs1.getSortValue().compareTo(smvfs2.getSortValue()))
        .collect(Collectors.toList());

    for (MultivariateFeatureStateValueModel multiVariate : sortedMultiVariateFeatureStates) {
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

  /**
   * Another FeatureStateModel is deemed to be higher priority if and only if
   * it has a FeatureSegment and either this.FeatureSegment is null or the
   * value of other.FeatureSegment.priority is lower than that of
   * this.FeatureSegment.priority.
   * 
   * @param other the other FeatureStateModel to compare priority wiht
   * @return true if `this` is higher priority than `other`
   */
  public boolean isHigherPriority(FeatureStateModel other) {
    if (this.featureSegment == null || other.featureSegment == null) {
      return this.featureSegment != null && other.featureSegment == null;
    }

    return this.featureSegment.getPriority() < other.featureSegment.getPriority();
  }
}
