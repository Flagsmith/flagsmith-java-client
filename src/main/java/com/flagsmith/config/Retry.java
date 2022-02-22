package com.flagsmith.config;

import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Retry {
  private Integer total = 1;
  private Integer attempts = 0;
  private Float backoffFactor = 0.1f;
  private Float backoffMax = 1f;
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

    return total > 0;
  }

  public Long calculateSleepTime() {
    return ((backoffFactor.longValue() * 1000) * (2 * attempts));
  }

  public void waitWithBackoff() throws InterruptedException {
    Thread.sleep(calculateSleepTime());
  }

  public void retryAttempted() {
    attempts++;
  }
}
