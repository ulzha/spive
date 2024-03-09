package io.ulzha.spive.lib;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages the lock used for synchronizing spontaneous emitIf from workloads.
 *
 * <p>Enforces that workloads can only emit between event handlers (when in-memory state is at rest,
 * appropriate for the associated pre-emit event validity check), and that they cannot emit between
 * an input event and its eventual consequential event destined for the same log. (Otherwise event
 * handler behavior would become nondeterministic due to nondeterministic results of _their_ emit()
 * calls.)
 */
public class EventLock {
  private final ReentrantLock spontaneousAppendLock = new ReentrantLock(true);

  /** Sets hold count to one unless we already hold it. */
  public void lock() {
    try {
      if (!spontaneousAppendLock.isHeldByCurrentThread()) {
        spontaneousAppendLock.lockInterruptibly();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  /**
   * Increments the hold count beyond the single hold that EventLoop initially got.
   *
   * <p>That way after the current handler completes, workloads will still be stopped from emitting,
   * until the corresponding consecutive event has been handled too.
   */
  public void lockConsequential() {
    spontaneousAppendLock.lock();
  }

  public void unlock() {
    spontaneousAppendLock.unlock();
  }
}
