package com.flagsmith.threads;

import com.flagsmith.FlagsmithClient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PollingManagerTest {
  private PollingManager manager;
  private FlagsmithClient client;

  @BeforeEach
  public void init() {
    client = mock(FlagsmithClient.class);
    manager = new PollingManager(client, 1);

    manager.startPolling();
  }

  @Test
  public void testPollingManager_checkPollingMethodInvoked() throws InterruptedException {
    verify(client, times(1)).updateEnvironment();
    Thread.sleep(1500);
    verify(client, times(2)).updateEnvironment();
  }

  @Test
  public void testPollingManager_checkPollingMethodInvokedAndStopped() throws InterruptedException {
    verify(client, times(1)).updateEnvironment();
    Thread.sleep(1500);
    verify(client, times(2)).updateEnvironment();
    manager.stopPolling();
    Thread.sleep(1500);
    verify(client, times(2)).updateEnvironment();
  }
}
