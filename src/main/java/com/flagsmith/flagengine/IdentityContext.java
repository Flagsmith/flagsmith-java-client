package com.flagsmith.flagengine;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IdentityContext
 *
 * <p>Represents an identity context for feature flag evaluation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "identifier",
    "key",
    "traits"
})
public class IdentityContext {

  /**
   * Identifier
   *
   * <p>A unique identifier for an identity, used for segment and multivariate
   * feature flag targeting, and displayed in the Flagsmith UI.
   * (Required)
   */
  @JsonProperty("identifier")
  @JsonPropertyDescription("A unique identifier for an identity, used for segment and multivariate "
      + "feature flag targeting, and displayed in the Flagsmith UI.")
  private String identifier;

  /**
   * Key
   *
   * <p>Key used when selecting a value for a multivariate feature, or for % split
   * segmentation. Set to an internal identifier or a composite value based on the
   * environment key and identifier, depending on Flagsmith implementation.
   * (Required)
   */
  @JsonProperty("key")
  @JsonPropertyDescription("Key used when selecting a value for a multivariate feature, "
      + "or for % split segmentation. Set to an internal identifier or a composite value "
      + "based on the environment key and identifier, depending on Flagsmith implementation.")
  private String key;

  /**
   * Traits
   *
   * <p>A map of traits associated with the identity, where the key is the trait name
   * and the value is the trait value.
   */
  @JsonProperty("traits")
  @JsonPropertyDescription("A map of traits associated with the identity, "
      + "where the key is the trait name and the value is the trait value.")
  private Traits traits;

  @JsonIgnore
  private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

  /**
   * No args constructor for use in serialization.
   */
  public IdentityContext() {
  }

  /**
   * Copy constructor.
   *
   * @param source the object being copied
   */
  public IdentityContext(IdentityContext source) {
    super();
    this.identifier = source.identifier;
    this.key = source.key;
    this.traits = source.traits;
  }

  /**
   * Constructor with required fields.
   *
   * @param identifier A unique identifier for an identity
   * @param key        Key used when selecting a value for a multivariate feature
   */
  public IdentityContext(String identifier, String key) {
    this.identifier = identifier;
    this.key = key;
  }

  /**
   * Constructor with all fields.
   *
   * @param identifier A unique identifier for an identity
   * @param key        Key used when selecting a value for a multivariate feature
   * @param traits     A map of traits associated with the identity
   */
  public IdentityContext(String identifier, String key, Traits traits) {
    this.identifier = identifier;
    this.key = key;
    this.traits = traits;
  }

  /**
   * Identifier
   *
   * <p>A unique identifier for an identity, used for segment and multivariate
   * feature flag targeting, and displayed in the Flagsmith UI.
   * (Required)
   */
  @JsonProperty("identifier")
  public String getIdentifier() {
    return identifier;
  }

  /**
   * Identifier
   *
   * <p>A unique identifier for an identity, used for segment and multivariate
   * feature flag targeting, and displayed in the Flagsmith UI.
   * (Required)
   */
  @JsonProperty("identifier")
  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  /**
   * Fluent setter for identifier.
   *
   * @param identifier A unique identifier for an identity
   * @return the IdentityContext instance
   */
  public IdentityContext withIdentifier(String identifier) {
    this.identifier = identifier;
    return this;
  }

  /**
   * Key
   *
   * <p>Key used when selecting a value for a multivariate feature, or for % split
   * segmentation. Set to an internal identifier or a composite value based on the
   * environment key and identifier, depending on Flagsmith implementation.
   * (Required)
   */
  @JsonProperty("key")
  public String getKey() {
    return key;
  }

  /**
   * Key
   *
   * <p>Key used when selecting a value for a multivariate feature, or for % split
   * segmentation. Set to an internal identifier or a composite value based on the
   * environment key and identifier, depending on Flagsmith implementation.
   * (Required)
   */
  @JsonProperty("key")
  public void setKey(String key) {
    this.key = key;
  }

  /**
   * Fluent setter for key.
   *
   * @param key the key
   * @return the IdentityContext instance
   */
  public IdentityContext withKey(String key) {
    this.key = key;
    return this;
  }

  /**
   * Traits
   *
   * <p>A map of traits associated with the identity, where the key is the trait name
   * and the value is the trait value.
   */
  @JsonProperty("traits")
  public Traits getTraits() {
    return traits;
  }

  /**
   * Traits
   *
   * <p>A map of traits associated with the identity, where the key is the trait name
   * and the value is the trait value.
   */
  @JsonProperty("traits")
  public void setTraits(Traits traits) {
    this.traits = traits;
  }

  /**
   * Fluent setter for traits.
   *
   * @param traits A map of traits associated with the identity
   * @return the IdentityContext instance
   */
  public IdentityContext withTraits(Traits traits) {
    this.traits = traits;
    return this;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }

  /**
   * Set additional property.
   *
   * @param name  the name
   * @param value the value
   */
  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

  /**
   * Fluent setter for additional properties.
   *
   * @param name  the name of the additional property
   * @param value the value of the additional property
   * @return the IdentityContext instance
   */
  public IdentityContext withAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
    return this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(IdentityContext.class.getName()).append('@')
        .append(Integer.toHexString(System.identityHashCode(this))).append('[');
    sb.append("identifier");
    sb.append('=');
    sb.append(((this.identifier == null) ? "<null>" : this.identifier));
    sb.append(',');
    sb.append("key");
    sb.append('=');
    sb.append(((this.key == null) ? "<null>" : this.key));
    sb.append(',');
    sb.append("traits");
    sb.append('=');
    sb.append(((this.traits == null) ? "<null>" : this.traits));
    sb.append(',');
    sb.append("additionalProperties");
    sb.append('=');
    sb.append(((this.additionalProperties == null) ? "<null>" : this.additionalProperties));
    sb.append(',');
    if (sb.charAt((sb.length() - 1)) == ',') {
      sb.setCharAt((sb.length() - 1), ']');
    } else {
      sb.append(']');
    }
    return sb.toString();
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = ((result * 31) + ((this.identifier == null) ? 0 : this.identifier.hashCode()));
    result = ((result * 31) + ((this.traits == null) ? 0 : this.traits.hashCode()));
    result = ((result * 31) + ((this.additionalProperties == null) ? 0
        : this.additionalProperties.hashCode()));
    result = ((result * 31) + ((this.key == null) ? 0 : this.key.hashCode()));
    return result;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof IdentityContext) == false) {
      return false;
    }
    IdentityContext rhs = ((IdentityContext) other);
    return (((((this.identifier == rhs.identifier)
        || ((this.identifier != null) && this.identifier.equals(rhs.identifier)))
        && ((this.traits == rhs.traits) || ((this.traits != null)
            && this.traits.equals(rhs.traits))))
        && ((this.additionalProperties == rhs.additionalProperties)
            || ((this.additionalProperties != null)
                && this.additionalProperties.equals(rhs.additionalProperties))))
        && ((this.key == rhs.key) || ((this.key != null)
            && this.key.equals(rhs.key))));
  }

}