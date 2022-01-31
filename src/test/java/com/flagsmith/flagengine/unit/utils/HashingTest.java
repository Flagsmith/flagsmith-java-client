package com.flagsmith.flagengine.unit.utils;

import com.flagsmith.flagengine.utils.Hashing;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Test(groups = "unit")
public class HashingTest {

  @DataProvider(name = "objectIds")
  public Object[][] getObjectIds() {
    return new String[][] {
        new String[] {"12", "93"},
        new String[] {UUID.randomUUID().toString(), "99"},
        new String[] {"99", UUID.randomUUID().toString()},
        new String[] {UUID.randomUUID().toString(), UUID.randomUUID().toString()}
    };
  }

  @Test(dataProvider = "objectIds")
  public void testHashedValueIsBetween100And0(String[] objectIds) {
    Float hashValue = Hashing.getInstance().getHashedPercentageForObjectIds(Arrays.asList(objectIds));
    Assert.assertTrue(hashValue < 100);
    Assert.assertTrue(hashValue >= 0);
  }

  @Test(dataProvider = "objectIds")
  public void testHashedValueIsSameEachTime(String[] objectIds) {
    Float hashValue = Hashing.getInstance().getHashedPercentageForObjectIds(Arrays.asList(objectIds));
    Float hashValueTheSecond = Hashing.getInstance().getHashedPercentageForObjectIds(Arrays.asList(objectIds));

    Assert.assertTrue(hashValue.equals(hashValueTheSecond));
  }

  @Test
  public void testPercentageValueIsUniqueForDifferentIdentifier() {
    List<String> objectIds1 = Arrays.asList("14", "106");
    List<String> objectIds2 = Arrays.asList("53", "200");

    Float hashValue = Hashing.getInstance().getHashedPercentageForObjectIds(objectIds1);
    Float hashValueTheSecond = Hashing.getInstance().getHashedPercentageForObjectIds(objectIds2);

    Assert.assertFalse(hashValue.equals(hashValueTheSecond));
  }

  @Test
  public void testPercentageValueIsEvenlyDistributed() {
    Integer testSample = 500;
    Integer numTestBuckets = 50;
    Integer testBucketSize = (int) testSample / numTestBuckets;
    Float errorFactor = 0.1f;

    List<List<String>> objectIds = IntStream
        .rangeClosed(0, testSample)
        .mapToObj(Integer::toString)
        .flatMap((objId1) -> IntStream
            .rangeClosed(0, testSample)
            .mapToObj((objId2) -> Arrays.asList(
                objId1,
                Integer.toString(objId2))
            ))
        .collect(Collectors.toList());

    List<Float> percentageValues = objectIds.stream()
        .map((ids) -> Hashing.getInstance().getHashedPercentageForObjectIds(ids))
        .sorted()
        .collect(Collectors.toList());

    List<Integer> numTestBucketRange = IntStream
        .rangeClosed(0, numTestBuckets).boxed().collect(Collectors.toList());

    for (Integer i : numTestBucketRange) {
      Integer bucketStart = i * testBucketSize;
      Integer bucketEnd = (i + 1) * testBucketSize;
      Float bucketValueLimit = Math.min(
          ((i + 1) + (errorFactor * (i + 1))) / numTestBuckets,
          1f
      );

      List<Integer> bucketRange = IntStream
          .rangeClosed(bucketStart, bucketEnd)
          .boxed()
          .collect(Collectors.toList());

      for (Integer bucket : bucketRange) {
        Float value = percentageValues.get(bucket);
        Assert.assertTrue(value <= bucketValueLimit);
      }
    }
  }

  public void testGetHashedPercentageIsNotOne() {
    List<String> objectIds = Arrays.asList("12", "93");

    String hashValue1 = "270e";
    String hashValue2 = "270f";
    final AtomicBoolean hashToReturn = new AtomicBoolean(Boolean.TRUE);

    Hashing hashingObject = Mockito.mock(Hashing.class, "getMD5");
    Hashing.setInstance(hashingObject);

    Mockito.when(hashingObject.getMD5(Mockito.anyString())).then(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocationOnMock) throws Throwable {

        String valueToReturn = hashValue2;
        if (hashToReturn.get()) {
          valueToReturn = hashValue1;
          hashToReturn.set(Boolean.FALSE);
        }

        return valueToReturn;
      }
    });

    Float percentageHash = hashingObject.getHashedPercentageForObjectIds(objectIds);
    Mockito.verify(hashingObject, Mockito.atMostOnce()).getMD5("12,93");
    Mockito.verify(hashingObject, Mockito.atMostOnce()).getMD5("12,93,12,93");

    Assert.assertEquals(percentageHash, 0f);

    Hashing.setInstance(null);
  }
}
