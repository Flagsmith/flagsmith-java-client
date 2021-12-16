package com.flagsmith;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representation of the Identity user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureUser {

  private String identifier;
}
