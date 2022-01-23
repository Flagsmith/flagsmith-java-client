package com.flagsmith.flagengine.unit.utils;

import com.flagsmith.flagengine.utils.Hashing;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.UUID;

@Test(groups = "unit")
public class HashingTest {

    @DataProvider(name = "objectIds")
    public Object[][] getObjectIds() {
        return new String[][]{
            new String[] {"12", "93"},
            new String[] {UUID.randomUUID().toString(), "99"},
            new String[] {"99", UUID.randomUUID().toString()},
            new String[] {UUID.randomUUID().toString(), UUID.randomUUID().toString()}
        };
    }

    @Test(dataProvider = "objectIds")
    public void testHashedValueIsBetween100And0(String[] objectIds) {
        Float hashValue = Hashing.getHashedPercentageForObjectIds(Arrays.asList(objectIds));
        Assert.assertTrue(hashValue < 100);
        Assert.assertTrue(hashValue >= 0);
    }

    @Test(dataProvider = "objectIds")
    public void testHashedValueIsSameEachTime(String[] objectIds) {
        Float hashValue = Hashing.getHashedPercentageForObjectIds(Arrays.asList(objectIds));
        Float hashValueTheSecond = Hashing.getHashedPercentageForObjectIds(Arrays.asList(objectIds));
//        System.out.println(hashValue);
//        System.out.println(hashValueTheSecond);
        Assert.assertTrue(hashValue.equals(hashValueTheSecond));
    }
}
