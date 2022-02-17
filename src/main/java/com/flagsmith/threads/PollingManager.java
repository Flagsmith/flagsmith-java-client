package com.flagsmith.threads;

import com.flagsmith.FlagsmithClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollingManager {

  FlagsmithClient client;
  Integer interval;
  Thread internalThread;
  Logger logger = LoggerFactory.getLogger(PollingManager.class);

  public PollingManager(FlagsmithClient client) {
    this(client, 10000);
  }

  public PollingManager(FlagsmithClient client, Integer interval) {
    this.client = client;
    this.interval = interval;
    this.internalThread = initializeThread();
  }

  /**
   * Initialize a thread in which to run the polling for environment
   * @return
   */
  private Thread initializeThread() {
    return new Thread() {
      @Override
      public void run() {
        try {
          while (!this.isInterrupted()) {
            client.updateEnvironment();
            Thread.sleep(interval);
          }
        } catch (InterruptedException e) {
          logger.info("Polling manager interrupted. Automatic environment update will stop!");
        }
      }
    };
  }

  /**
   * Start polling of the environment in the new thread
   */
  public void startPolling() {
    internalThread.start();
  }

  /**
   * Stop polling of the new environment
   */
  public void stopPolling() {
    internalThread.interrupt();
  }

  /**
   * Clean up if the object is destroyed or GCed.
   */
  protected void finalize() {
    stopPolling();
  }

}
