package com.flagsmith.models;

import com.flagsmith.utils.models.BaseModel;
import lombok.Data;

@Data
public class BaseFlag extends BaseModel {
  private Boolean enabled;
  private Object value;
  private String featureName;

  /**
   * The reason the flag was evaluated to its value, e.g. {@code STATIC} or
   * {@code TARGETING_MATCH; segment=premium_users}.
   *
   * @return the reason, or null if the flag was not evaluated by Flagsmith, e.g. a default flag
   */
  public String getReason() {
    return null;
  }
}
