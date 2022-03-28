package com.flagsmith.models;

import lombok.Data;

@Data
public class DefaultFlag extends BaseFlag {
  private Boolean isDefault = Boolean.TRUE;
}
