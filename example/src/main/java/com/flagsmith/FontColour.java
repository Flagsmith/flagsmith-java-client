package com.flagsmith;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FontColour {
  @JsonProperty("colour")
  private String colour;

  public String getColour() {
    return colour;
  }

  public void setColour(String colour) {
    this.colour = colour;
  }
}
