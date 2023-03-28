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
    manager = new PollingManager(client, 1);

    manager.startPolling();
  }

  @Test(groups = "unit")
  public void testPollingManager_checkPollingMethodInvoked() throws InterruptedException {
    verify(client, times(1)).updateEnvironment();
    Thread.sleep(1500);
    verify(client, times(2)).updateEnvironment();
  }

  @Test(groups = "unit")
  public void testPollingManager_checkPollingMethodInvokedAndStopped() throws InterruptedException {
    verify(client, times(1)).updateEnvironment();
    Thread.sleep(1500);
    verify(client, times(2)).updateEnvironment();
    manager.stopPolling();
    Thread.sleep(1500);
    verify(client, times(2)).updateEnvironment();
  }
}
