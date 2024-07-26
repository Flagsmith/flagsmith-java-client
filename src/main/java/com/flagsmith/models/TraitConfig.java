package com.flagsmith.models;

/**
 * Use TraitConfig to provide a trait value
 * along with additional parameters.
 * 
 * <p>Construct a transient trait value:</p>
 * <p><code>new TraitConfig("value", true);</code></p>
 */
public class TraitConfig {
  private final Object value;
  private final boolean isTransient;

  private TraitConfig(Object value) {
    this.value = value;
    this.isTransient = false;
  }

  /**
   * Get a TraitConfig instance.
   * @param value a trait value object
   * @param isTransient whether the trait is transient
   */
  public TraitConfig(Object value, boolean isTransient) {
    this.value = value;
    this.isTransient = isTransient;
  }

  /**
   * Get trait value.
   * @return the trait value
   */
  public Object getValue() {
    return this.value;
  }

  /**
   * Get a boolean indicating whether the trait is transient.
   * @return the boolean transiency value
   */
  public boolean getIsTransient() {
    return this.isTransient;
  }

  /**
   * Convert a user-provided object to a TraitConfig instance.
   * 
   * @param object an object or a TraitConfig instance
   * @return the TraitConfig instance
   */
  public static TraitConfig fromObject(Object object) {
    if (object instanceof TraitConfig) {
      return (TraitConfig) object;
    }
    return new TraitConfig(object);
  }
}
