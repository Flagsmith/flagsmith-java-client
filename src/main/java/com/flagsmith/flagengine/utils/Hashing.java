package com.flagsmith.flagengine.utils;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Hashing {

    public static Float getHashedPercentageForObjectIds(List<String> ids) {
        return getHashedPercentageForObjectIds(ids, 1);
    }

    public static Float getHashedPercentageForObjectIds(List<String> ids, Integer iterations) {
        String hashTo = ids.stream().collect(Collectors.joining(","));
        String hashToWithIteration = IntStream
                .rangeClosed(1, iterations)
                .mapToObj((i) -> hashTo)
                .collect(Collectors.joining(","));

        String hashedString = getMD5(hashToWithIteration);
        BigInteger hashedBigInteger = new BigInteger(hashedString, 16);
        Float hashedFloat = hashedBigInteger.mod( new BigInteger("9999") ).floatValue();
        hashedFloat = ((hashedFloat / 9998) * 100);

        if (hashedFloat == 100) {
            return getHashedPercentageForObjectIds(ids, iterations + 1);
        }

        return hashedFloat;
    }

    private static String getMD5(String hash) {
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

        } catch (UnsupportedEncodingException e) {

        }
        return null;
    }
}
