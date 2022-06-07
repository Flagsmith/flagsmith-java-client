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

  public Retry(Integer total) {
    this.total = total;
  }

  /**
   * Should Retry or not?.
   *
   * @param statusCode status code of last call
   * @return
   */
  public Boolean isRetry(Integer statusCode) {
    if (statusForcelist != null && !statusForcelist.isEmpty()
        && statusForcelist.contains(statusCode)) {
      return Boolean.TRUE;
    }

    return total > attempts;
  }

  /**
   * The sleep time based on back off factor.
   *
   * @return
   */
  public Long calculateSleepTime() {
    Float holdTimeMS = ((backoffFactor) * (2 * attempts));
    if (holdTimeMS >= backoffMax) {
      holdTimeMS = backoffMax;
    }

    return Float.valueOf(holdTimeMS * 1000f).longValue();
  }

  public void waitWithBackoff() throws InterruptedException {
    Thread.sleep(calculateSleepTime());
  }

  public void retryAttempted() {
    attempts++;
  }
}
