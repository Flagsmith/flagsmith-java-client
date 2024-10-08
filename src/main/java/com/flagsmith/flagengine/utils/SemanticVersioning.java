package com.flagsmith.flagengine.utils;

public class SemanticVersioning {

  /**
   * Checks if the given string have `:semver` suffix or not.
   * <pre>{@code
   *    >>> is_semver("2.1.41-beta:semver")
   *    True
   *    >>> is_semver("2.1.41-beta")
   *    False
   * }</pre>
   *
   * @param version The version string.
   */
  public static Boolean isSemver(String version) {
    return version.endsWith(":semver");
  }

  /**
   * Remove the semver suffix(i.e: last 7 characters) from the given value.
   * <pre>{@code
   *     >>> remove_semver_suffix("2.1.41-beta:semver")
   *     '2.1.41-beta'
   *     >>> remove_semver_suffix("2.1.41:semver")
   *     '2.1.41'
   * }</pre>
   *
   * @param version the version string to strip version from.
   */
  public static String removeSemver(String version) {
    return version.substring(0, version.length() - 7);
  }
}
