package com.flagsmith.flagengine.unit.Identities;

import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.fixtures.FlagEngineFixtures;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IdentitiesModelTest {

  @Test
  public void testCompositeKey() {
    String environmentApiKey = "abc123";
    String identifier = "identity";

    IdentityModel identity = new IdentityModel();
    identity.setEnvironmentApiKey(environmentApiKey);
    identity.setIdentifier(identifier);

    Assertions.assertEquals(identity.getCompositeKey(), environmentApiKey + "_" + identifier);
  }

  @Test
  public void testIdentityModelCreatesDefaultIdentityUuid() {
    String environmentApiKey = "abc123";
    String identifier = "identity";

    IdentityModel identity = new IdentityModel();
    identity.setEnvironmentApiKey(environmentApiKey);
    identity.setIdentifier(identifier);

    Assertions.assertNotNull(identity.getIdentityUuid());
  }

  @Test
  public void testUpdateTraitsRemoveTraitsWithNoneValue() {
    IdentityModel identity = FlagEngineFixtures.identityInSegment();

    TraitModel traitToRemove = new TraitModel();
    traitToRemove.setTraitKey(identity.getIdentityTraits().get(0).getTraitKey());

    identity.updateTraits(Arrays.asList(traitToRemove));

    Assertions.assertNotNull(identity.getIdentityTraits());
    Assertions.assertEquals(identity.getIdentityTraits().size(), 0);
  }

  @Test
  public void testUpdateIdentityTraitsUpdatesTraitValue() {
    IdentityModel identity = FlagEngineFixtures.identityInSegment();

    TraitModel traitToUpdate = new TraitModel();
    traitToUpdate.setTraitKey(identity.getIdentityTraits().get(0).getTraitKey());
    traitToUpdate.setTraitValue("updated");

    identity.updateTraits(Arrays.asList(traitToUpdate));

    Assertions.assertNotNull(identity.getIdentityTraits());
    Assertions.assertEquals(identity.getIdentityTraits().size(), 1);
    Assertions.assertEquals(identity.getIdentityTraits().get(0), traitToUpdate);
  }

  @Test
  public void testUpdateTraitsAddsNewTraits() {
    IdentityModel identity = FlagEngineFixtures.identityInSegment();

    TraitModel traitToUpdate = new TraitModel();
    traitToUpdate.setTraitKey("new");
    traitToUpdate.setTraitValue("updated");

    identity.updateTraits(Arrays.asList(traitToUpdate));

    Assertions.assertNotNull(identity.getIdentityTraits());
    Assertions.assertEquals(identity.getIdentityTraits().size(), 2);

    Boolean isPresent = identity.getIdentityTraits().stream()
        .anyMatch((it) -> it.equals(traitToUpdate));

    Assertions.assertTrue(isPresent);
  }

  @Test
  public void testAppendFeatureState() {
    FeatureStateModel fs1 = FlagEngineFixtures.featureState1();
    fs1.setEnabled(false);

    IdentityModel identity = FlagEngineFixtures.identity();
    identity.getIdentityFeatures().add(fs1);

    Boolean isPresent = identity.getIdentityFeatures().stream()
        .anyMatch((fs) -> fs.equals(fs1));

    Assertions.assertTrue(isPresent);
  }
}
