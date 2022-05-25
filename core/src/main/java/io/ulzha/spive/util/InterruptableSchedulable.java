package io.ulzha.spive.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper for scheduling indefinitely in a ScheduledExecutorService except when interrupted. */
public class InterruptableSchedulable implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(InterruptableSchedulable.class);
  // MethodHandles.lookup().lookupClass() TOLEARN?
  // Other projects have mega SafeRunnable

  private final Runnable delegate;

  public interface InterruptableRunnable {
    void run() throws InterruptedException;
  }

  public InterruptableSchedulable(final InterruptableRunnable interruptableDelegate) {
    this.delegate =
        () -> {
          try {
            interruptableDelegate.run();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Scheduled runnable was interrupted. Exiting", e);
          }
        };
  }

  @Override
  public void run() {
    try {
      delegate.run();
    } catch (Throwable t) {
      if (t.getCause() instanceof InterruptedException) {
        // cancel scheduling
        throw t;
      } else {
        // intentionally swallow so that scheduled execution continues
        LOG.info("Scheduled runnable failed. Trying again later", t);
      }
    }
  }
}
