package com.flagsmith.flagengine;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traits
 *
 * <p>A map of traits associated with the identity, where the key is the trait name
 * and the value is the trait value.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({

})
public class Traits {

  @JsonIgnore
  private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

  /**
   * No args constructor for use in serialization.
   */
  public Traits() {
  }

  /**
   * Copy constructor.
   *
   * @param source the object being copied
   */
  public Traits(Traits source) {
    super();
    this.additionalProperties = new LinkedHashMap<>(source.additionalProperties);
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }

  /**
   * Set additional property.
   *
   * @param name the name
   * @param value the value
   */
  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

  /**
   * Fluent setter for additional property.
   *
   * @param name the name
   * @param value the value
   * @return the Traits instance
   */
  public Traits withAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
    return this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(Traits.class.getName()).append('@')
        .append(Integer.toHexString(System.identityHashCode(this))).append('[');
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
    result = ((result * 31) + ((this.additionalProperties == null) ? 0
        : this.additionalProperties.hashCode()));
    return result;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof Traits) == false) {
      return false;
    }
    Traits rhs = ((Traits) other);
    return ((this.additionalProperties == rhs.additionalProperties)
        || ((this.additionalProperties != null)
        && this.additionalProperties.equals(rhs.additionalProperties)));
  }

}