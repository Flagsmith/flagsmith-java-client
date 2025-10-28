package com.flagsmith;

public final class Versions {
  private Versions() {}

  public static String getVersion() {
    String version = Versions.class.getPackage().getImplementationVersion();
    return version != null ? version : "unknown";
  }
}
