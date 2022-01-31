package com.flagsmith.flagengine.unit.Identities;

import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.fixtures.FlagEngineFixtures;
import com.flagsmith.flagengine.identities.IdentityModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import java.util.Arrays;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test(groups = "unit")
public class IdentitiesModelTest {

  public void testCompositeKey() {
    String environmentApiKey = "abc123";
    String identifier = "identity";

    IdentityModel identity = new IdentityModel();
    identity.setEnvironmentApiKey(environmentApiKey);
    identity.setIdentifier(identifier);

    Assert.assertEquals(identity.getCompositeKey(), environmentApiKey + "_" + identifier);
  }

  public void testIdentityModelCreatesDefaultIdentityUuid() {
    String environmentApiKey = "abc123";
    String identifier = "identity";

    IdentityModel identity = new IdentityModel();
    identity.setEnvironmentApiKey(environmentApiKey);
    identity.setIdentifier(identifier);

    Assert.assertNotNull(identity.getIdentityUuid());
  }

  public void testUpdateTraitsRemoveTraitsWithNoneValue() {
    IdentityModel identity = FlagEngineFixtures.identityInSegment();

    TraitModel traitToRemove = new TraitModel();
    traitToRemove.setTraitKey(identity.getIdentityTraits().get(0).getTraitKey());

    identity.updateTraits(Arrays.asList(traitToRemove));

    Assert.assertNotNull(identity.getIdentityTraits());
    Assert.assertEquals(identity.getIdentityTraits().size(), 0);
  }

  public void testUpdateIdentityTraitsUpdatesTraitValue() {
    IdentityModel identity = FlagEngineFixtures.identityInSegment();

    TraitModel traitToUpdate = new TraitModel();
    traitToUpdate.setTraitKey(identity.getIdentityTraits().get(0).getTraitKey());
    traitToUpdate.setTraitValue("updated");

    identity.updateTraits(Arrays.asList(traitToUpdate));

    Assert.assertNotNull(identity.getIdentityTraits());
    Assert.assertEquals(identity.getIdentityTraits().size(), 1);
    Assert.assertEquals(identity.getIdentityTraits().get(0), traitToUpdate);
  }

  public void testUpdateTraitsAddsNewTraits() {
    IdentityModel identity = FlagEngineFixtures.identityInSegment();

    TraitModel traitToUpdate = new TraitModel();
    traitToUpdate.setTraitKey("new");
    traitToUpdate.setTraitValue("updated");

    identity.updateTraits(Arrays.asList(traitToUpdate));

    Assert.assertNotNull(identity.getIdentityTraits());
    Assert.assertEquals(identity.getIdentityTraits().size(), 2);

    Boolean isPresent = identity.getIdentityTraits().stream()
        .anyMatch((it) -> it.equals(traitToUpdate));

    Assert.assertTrue(isPresent);
  }

  public void testAppendFeatureState() {
    FeatureStateModel fs1 = FlagEngineFixtures.featureState1();
    fs1.setEnabled(false);

    IdentityModel identity = FlagEngineFixtures.identity();
    identity.getIdentityFeatures().add(fs1);

    Boolean isPresent = identity.getIdentityFeatures().stream()
        .anyMatch((fs) -> fs.equals(fs1));

    Assert.assertTrue(isPresent);
  }
}
