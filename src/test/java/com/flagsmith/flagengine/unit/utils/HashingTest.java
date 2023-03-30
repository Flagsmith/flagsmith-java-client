package com.flagsmith.flagengine.unit.utils;

import com.flagsmith.flagengine.utils.Hashing;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class HashingTest {

  private static Stream<Arguments> getObjectIds() {
    return Stream.of(
        Arguments.of("12", "93"),
        Arguments.of(UUID.randomUUID().toString(), "99"),
        Arguments.of("99", UUID.randomUUID().toString()),
        Arguments.of(UUID.randomUUID().toString(), UUID.randomUUID().toString())
    );
  }

  @ParameterizedTest
  @MethodSource("getObjectIds")
  public void testHashedValueIsBetween100And0(String first, String second) {
    String[] objectIds = new String[] {first, second};
    Float hashValue = Hashing.getInstance().getHashedPercentageForObjectIds(Arrays.asList(objectIds));
    Assertions.assertTrue(hashValue < 100);
    Assertions.assertTrue(hashValue >= 0);
  }

  @ParameterizedTest
  @MethodSource("getObjectIds")
  public void testHashedValueIsSameEachTime(String first, String second) {
    String[] objectIds = new String[] {first, second};

    Float hashValue = Hashing.getInstance().getHashedPercentageForObjectIds(Arrays.asList(objectIds));
    Float hashValueTheSecond = Hashing.getInstance().getHashedPercentageForObjectIds(Arrays.asList(objectIds));

    Assertions.assertTrue(hashValue.equals(hashValueTheSecond));
  }

  @Test
  public void testPercentageValueIsUniqueForDifferentIdentifier() {
    List<String> objectIds1 = Arrays.asList("14", "106");
    List<String> objectIds2 = Arrays.asList("53", "200");

    Float hashValue = Hashing.getInstance().getHashedPercentageForObjectIds(objectIds1);
    Float hashValueTheSecond = Hashing.getInstance().getHashedPercentageForObjectIds(objectIds2);

    Assertions.assertFalse(hashValue.equals(hashValueTheSecond));
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
        Assertions.assertTrue(value <= bucketValueLimit);
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

    Assertions.assertEquals(percentageHash, 0f);

    Hashing.setInstance(null);
  }
}
