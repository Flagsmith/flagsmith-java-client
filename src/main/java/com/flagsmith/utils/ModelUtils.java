package com.flagsmith.utils;

import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.models.SdkTraitModel;
import com.flagsmith.models.TraitConfig;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class ModelUtils {
  /**
   * Convert a user-provided trait map to a list of trait models.
   * 
   * @param traits a map of traits, String trait key to Object/TraitConfig value
   * @return the list of trait models
   */
  public static List<TraitModel> getTraitModelsFromTraitMap(Map<String, Object> traits) {
    return ModelUtils.getTraitModelStreamFromTraitMap(
      traits, () -> new TraitModel()).map(Pair::getLeft).collect(Collectors.toList());
  }

  /**
   * Convert a user-provided trait map to a list of trait models
   * with transiency info.
   * 
   * @param traits a map of traits, String trait key to Object/TraitConfig value
   * @return the list of trait models with transiency info
   */
  public static List<SdkTraitModel> getSdkTraitModelsFromTraitMap(Map<String, Object> traits) {
    return ModelUtils.getTraitModelStreamFromTraitMap(traits, () -> new SdkTraitModel()).map(
      (row) -> {
        SdkTraitModel sdkTraitModel = row.getLeft();
        TraitConfig traitConfig = row.getRight();
        sdkTraitModel.setIsTransient(traitConfig.getIsTransient());
        return sdkTraitModel;
      }
    ).collect(Collectors.toList());
  }

  private static Stream<Entry<String, TraitConfig>> getTraitConfigStreamFromTraitMap(
      Map<String, Object> traits
  ) {
    return traits.entrySet().stream().map(
        (row) -> {
          return Map.entry(
            row.getKey(),
            TraitConfig.fromObject(row.getValue())
          );
        }
    );
  }

  private static <T extends TraitModel> Stream<Pair<T, TraitConfig>>
      getTraitModelStreamFromTraitMap(
        Map<String, Object> traits, Supplier<T> traitSupplier
  ) {
    return ModelUtils.getTraitConfigStreamFromTraitMap(traits).map(
        (row) -> {
          T trait = traitSupplier.get();
          TraitConfig traitConfig = row.getValue();
          trait.setTraitKey(row.getKey());
          trait.setTraitValue(traitConfig.getValue());
          return new ImmutablePair<T, TraitConfig>(trait, traitConfig);
        });
  }
}
