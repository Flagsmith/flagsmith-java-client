package com.flagsmith;

import com.flagsmith.config.Retry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class FlagsmithRetryTest {

  @Test
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

  @Test
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

  @Test
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

  private static Retry oneRetryOnServerErrors() {
    Retry retry = new Retry(2);
    retry.setStatusForcelist(new HashSet<>(Arrays.asList(500, 502, 503, 504)));
    retry.setStatusForcelistOnly(Boolean.TRUE);
    return retry;
  }

  @Test
  public void FlagsmithRetry_statusForcelistOnly_retriesForcedStatusWithinBudget() {
    Retry retry = oneRetryOnServerErrors();

    retry.retryAttempted();
    assertTrue(retry.isRetry(503), "a force-listed status should retry while attempts remain");
  }

  @Test
  public void FlagsmithRetry_statusForcelistOnly_stopsAtTheAttemptsBudget() {
    Retry retry = oneRetryOnServerErrors();

    retry.retryAttempted();
    retry.retryAttempted();
    // Without this bound a permanently failing endpoint retries forever.
    assertFalse(retry.isRetry(503), "a force-listed status must not retry past the budget");
    assertFalse(retry.isRetry(null), "a connection failure must not retry past the budget");
  }

  @Test
  public void FlagsmithRetry_statusForcelistOnly_doesNotRetryUnlistedStatus() {
    Retry retry = oneRetryOnServerErrors();

    retry.retryAttempted();
    assertFalse(retry.isRetry(400), "a 4xx must not be retried");
    assertFalse(retry.isRetry(404), "a 4xx must not be retried");
  }

  @Test
  public void FlagsmithRetry_statusForcelistOnly_retriesConnectionFailuresWithinBudget() {
    Retry retry = oneRetryOnServerErrors();

    retry.retryAttempted();
    assertTrue(retry.isRetry(null), "a connection failure should retry while attempts remain");
  }

  @Test
  public void FlagsmithRetry_defaultPolicyIsUnchanged() {
    Retry retry = new Retry(1);

    assertFalse(retry.getStatusForcelistOnly());
    retry.retryAttempted();
    // Historical behaviour: a force-listed status retries regardless of the budget.
    assertTrue(retry.isRetry(503));
    assertFalse(retry.isRetry(401));
  }

  @Test
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
