package com.flagsmith.flagengine.features;

import lombok.Data;

@Data
public class FeatureModel {
  private Integer id;
  private String name;
  private String type;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof FeatureModel)) {
      return false;
    }

    return id != null && id.equals(((FeatureModel) o).getId());
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
