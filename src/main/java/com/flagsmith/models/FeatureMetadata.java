package com.flagsmith.models;

/**
 * FeatureMetadata
 * 
 * <p>Additional metadata associated with a feature.
 * 
 */
public class FeatureMetadata {
  private Integer flagsmithId;

  /*
   * FlagsmithId
   * <p>The internal Flagsmith ID for the feature.
   * 
   * @return The flagsmithId
   */
  public Integer getFlagsmithId() {
    return flagsmithId;
  }

  /*
   * FlagsmithId
   * <p>The internal Flagsmith ID for the segment.
   * 
   * @param flagsmithId The flagsmithId
   */
  public void setFlagsmithId(Integer flagsmithId) {
    this.flagsmithId = flagsmithId;
  }
}
