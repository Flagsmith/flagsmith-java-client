package com.flagsmith;

import com.flagsmith.config.Retry;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

@Test(groups="unit")
public class FlagsmithRetryTest {

  public void FlagsmithRetry_simpleInstance() {
    Retry retryObject = new Retry(3);

    assertNotNull(retryObject);
    assertTrue(retryObject.getTotal().equals(3));
    assertTrue(retryObject.getAttempts().equals(0));
  }

  public void FlagsmithRetry_verifyIncreasedAttempt() {
    Retry retryObject = new Retry(3);

    assertNotNull(retryObject);
    assertTrue(retryObject.getTotal().equals(3));
    assertTrue(retryObject.getAttempts().equals(0));
    retryObject.retryAttempted();
    assertTrue(retryObject.getAttempts().equals(1));
  }

  public void FlagsmithRetry_shouldExactlyRunThreeTimes() {
    Retry retryObject = new Retry(3);

    assertNotNull(retryObject);
    assertTrue(retryObject.getTotal().equals(3));
    assertTrue(retryObject.getAttempts().equals(0));
    Integer attempts = 0;
    do {
      retryObject.retryAttempted();
      attempts++;
      assertTrue(retryObject.getAttempts().equals(attempts));
    } while(retryObject.isRetry(401));
    assertTrue(attempts.equals(3));
  }

  public void FlagsmithRetry_validateSleep() {
    Retry retryObject = new Retry(3);
    List<Long> delays = new ArrayList<Long>() {{
      add(0l);
      add(200l);
      add(400l);
    }};

    assertNotNull(retryObject);
    assertTrue(retryObject.getTotal().equals(3));
    assertTrue(retryObject.getAttempts().equals(0));
    Integer attempts = 0;
    do {
      assertEquals(retryObject.calculateSleepTime(), delays.get(attempts));
      retryObject.retryAttempted();
      attempts++;
    } while(retryObject.isRetry(401));
    assertTrue(attempts.equals(3));
  }

  public void FlagsmithRetry_shouldNotExceedBackoffMax() {
    Retry retryObject = new Retry(7);
    retryObject = retryObject.toBuilder().backoffMax(1f).build();
    List<Long> delays = new ArrayList<Long>() {{
      add(0l);
      add(200l);
      add(400l);
      add(600l);
      add(800l);
      add(1000l);
      add(1000l);
    }};

    assertNotNull(retryObject);
    assertTrue(retryObject.getTotal().equals(7));
    assertTrue(retryObject.getAttempts().equals(0));
    Integer attempts = 0;
    do {
      assertEquals(retryObject.calculateSleepTime(), delays.get(attempts));
      retryObject.retryAttempted();
      attempts++;
    } while(retryObject.isRetry(401));
    assertTrue(attempts.equals(7));
  }
}
