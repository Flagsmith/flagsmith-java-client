package com.flagsmith.flagengine.models;

import com.flagsmith.models.Flag;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlagTest {
  @Test
  public void testToString() {
    Flag flag = new Flag();
    flag.setEnabled(true);
    flag.setIsDefault(false);
    flag.setFeatureName("my_feature");
    flag.setValue("foo");
    flag.setFeatureId(1);

    String expected = String.join("Flag(super=",
                                   "BaseFlag(enabled=true, ",
                                   "value=foo, ",
                                   "featureName=my_feature), ",
                                   "featureId=1, ",
                                   "isDefault=false)");

    assertEquals(expected, flag.toString());
  }
}
