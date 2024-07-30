package com.flagsmith.flagengine.utils;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hashing {

  private static Hashing instance = null;
  private static Logger logger = LoggerFactory.getLogger(Hashing.class);

  private Hashing() {}

  /**
   * Returns the hashing instance.
   */
  public static Hashing getInstance() {
    if (instance == null) {
      setInstance(new Hashing());
    }

    return instance;
  }

  /**
   * Set the instance object.
   *
   * @param instanceObj Instance obj of Hashing
   */
  public static void setInstance(Hashing instanceObj) {
    instance = instanceObj;
  }

  /**
   * Returns the percentage of hash of the list of object IDs.
   *
   * @param ids List of string IDs
   */
  public Float getHashedPercentageForObjectIds(List<String> ids) {
    return getHashedPercentageForObjectIds(ids, 1);
  }

  /**
   * Returns the percentage of hash of the list of object IDs with iteration.
   *
   * @param ids List of string IDs.
   * @param iterations Number of iterations for the string to be repeated.
   */
  public Float getHashedPercentageForObjectIds(List<String> ids, Integer iterations) {
    String hashTo = ids.stream().collect(Collectors.joining(","));
    String hashToWithIteration = IntStream
        .rangeClosed(1, iterations)
        .mapToObj((i) -> hashTo)
        .collect(Collectors.joining(","));

    String hashedString = getMD5(hashToWithIteration);
    BigInteger hashedBigInteger = new BigInteger(hashedString, 16);
    Float hashedFloat = hashedBigInteger.mod(new BigInteger("9999")).floatValue();
    hashedFloat = ((hashedFloat / 9998) * 100);

    if (hashedFloat == 100) {
      return getHashedPercentageForObjectIds(ids, iterations + 1);
    }

    return hashedFloat;
  }

  /**
   * returns the mdt of the string provided.
   *
   * @param hash String to be hashed.
   */
  public String getMD5(String hash) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      md.update(hash.getBytes("UTF-8"));
      byte[] digest = md.digest();

      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02X", b));
      }
      return sb.toString();

    } catch (NoSuchAlgorithmException e) {
      logger.error("MD5 Hashing Algorithm not found.");
    } catch (UnsupportedEncodingException e) {
      logger.error("Encoding not supported.");
    }

    return null;
  }
}
