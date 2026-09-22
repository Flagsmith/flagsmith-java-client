package com.flagsmith.config;

import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An implementation in Java for the Retry component of the Python urllib3 library.
 * https://urllib3.readthedocs.io/en/latest/reference/urllib3.util.html#urllib3.util.Retry
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Retry {
  private Integer total = 1;
  private Integer attempts = 0;
  private Float backoffFactor = 0.1f;
  private Float backoffMax = 15f;
  private Set<Integer> statusForcelist = new HashSet<Integer>() {{
      add(413);
      add(429);
      add(503);
    }};
  /**
   * When true, a response is only retried if its status code is in {@link #statusForcelist}, and
   * never beyond the {@link #total} attempts budget. A connection failure (a null status code) is
   * still retried while attempts remain. Defaults to false, which keeps the historical behaviour
   * of retrying any status while attempts remain, and retrying a force-listed status regardless of
   * the budget.
   */
  private Boolean statusForcelistOnly = Boolean.FALSE;

  public Retry(Integer total) {
    this.total = total;
  }

  /**
   * Instantiate without a status forcelist policy, which keeps the historical behaviour.
   *
   * @param total           number of attempts before giving up
   * @param attempts        attempts made so far
   * @param backoffFactor   factor applied to the backoff between attempts
   * @param backoffMax      upper bound on the backoff, in seconds
   * @param statusForcelist status codes that are always retried
   */
  public Retry(Integer total, Integer attempts, Float backoffFactor, Float backoffMax,
      Set<Integer> statusForcelist) {
    this(total, attempts, backoffFactor, backoffMax, statusForcelist, Boolean.FALSE);
  }

  /**
   * Should Retry or not?.
   *
   * @param statusCode status code of last call, or null if the call did not get a response
   */
  public Boolean isRetry(Integer statusCode) {
    if (Boolean.TRUE.equals(statusForcelistOnly)) {
      if (total <= attempts) {
        return Boolean.FALSE;
      }
      return statusCode == null
          || (statusForcelist != null && statusForcelist.contains(statusCode));
    }

    if (statusForcelist != null && !statusForcelist.isEmpty()
        && statusForcelist.contains(statusCode)) {
      return Boolean.TRUE;
    }

    return total > attempts;
  }

  /**
   * The sleep time based on back off factor.
   */
  public Long calculateSleepTime() {
    Float holdTimeMs = ((backoffFactor) * (2 * attempts));
    if (holdTimeMs >= backoffMax) {
      holdTimeMs = backoffMax;
    }

    return Float.valueOf(holdTimeMs * 1000f).longValue();
  }

  public void waitWithBackoff() throws InterruptedException {
    Thread.sleep(calculateSleepTime());
  }

  public void retryAttempted() {
    attempts++;
  }
}
