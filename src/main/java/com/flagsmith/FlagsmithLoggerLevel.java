package com.flagsmith;

public enum FlagsmithLoggerLevel {
  INFO(1),
  ERROR(2);

  private final int key;

  FlagsmithLoggerLevel(int key) {
    this.key = key;
  }

  public int getValue() {
    return key;
  }
}
