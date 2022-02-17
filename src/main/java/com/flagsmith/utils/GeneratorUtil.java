package com.flagsmith.utils;

import com.flagsmith.IdentityTraits;
import com.flagsmith.Trait;
import java.util.Map;
import java.util.stream.Collectors;

public class GeneratorUtil {

  /**
   * Generate identity traits with identifier and key value map.
   * @param identifier identifier string
   * @param traits key value map traits
   * @return
   */
  public static IdentityTraits generateIdentitiesData(
      String identifier, Map<String, String> traits) {
    IdentityTraits identityTraits = new IdentityTraits();
    identityTraits.setIdentifier(identifier);
    identityTraits.setTraits(
        traits.entrySet()
            .stream().map((entry) -> {
              Trait trait = new Trait();
              trait.setKey(entry.getKey());
              trait.setValue(entry.getValue());

              return trait;
            }).collect(Collectors.toList())
    );

    return identityTraits;
  }
}
