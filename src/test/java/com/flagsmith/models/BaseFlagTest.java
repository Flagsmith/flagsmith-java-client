package com.flagsmith.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class BaseFlagTest {

  @Test
  public void flagReasonIsReadableAsBaseFlag() {
    // Given
    Flag flag = new Flag();
    flag.setReason("TARGETING_MATCH; segment=premium_users");

    // When
    BaseFlag baseFlag = flag;

    // Then
    assertEquals("TARGETING_MATCH; segment=premium_users", baseFlag.getReason());
  }

  @Test
  public void defaultFlagHasNoReason() {
    // Given
    BaseFlag defaultFlag = new DefaultFlag();

    // When
    String reason = defaultFlag.getReason();

    // Then
    assertNull(reason);
  }
}
