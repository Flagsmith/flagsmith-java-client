package com.flagsmith.threads;

import com.flagsmith.FlagsmithClient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups="unit")
public class PollingManagerTest {
  private PollingManager manager;
  private FlagsmithClient client;

  @BeforeMethod(groups = "unit")
  public void init() {
    client = mock(FlagsmithClient.class);
    manager = new PollingManager(client, 100);

    manager.startPolling();
  }

  public void PollingManagerTest_checkPollingMethodInvoked() throws InterruptedException {
    Thread.sleep(50);
    verify(client, times(1)).updateEnvironment();
    Thread.sleep(110);
    verify(client, times(2)).updateEnvironment();
  }

  public void PollingManagerTest_checkPollingMethodInvokedAndStopped() throws InterruptedException {
    Thread.sleep(50);
    verify(client, times(1)).updateEnvironment();
    manager.stopPolling();
    Thread.sleep(110);
    verify(client, times(1)).updateEnvironment();
  }
}
