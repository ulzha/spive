package io.ulzha.spive.lib;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the lock used for synchronizing spontaneous emitIf from workloads.
 *
 * <p>Enforces that workloads can only emit between event handlers (when in-memory state is at rest,
 * appropriate for the associated pre-emit event validity check), and that they cannot emit between
 * an input event and its eventual consequential event destined for the same log. (Otherwise event
 * handler behavior would become nondeterministic due to nondeterministic results of _their_ emit()
 * calls.)
 */
//
public class LockableEventLog implements EventLog {
  private final EventLog delegate;
  private final ReentrantLock spontaneousAppendLock = new ReentrantLock(true);

  public LockableEventLog(final EventLog delegate) {
    this.delegate = delegate;
    // TODO Until EOF is read we should probably never even open the lock, no appendIfPrevTimeMatch
    // can go through anyway...
  }

  @Override
  public void close() throws Exception {
    delegate.close();
  }

  @Override
  @NotNull
  public Iterator<EventEnvelope> iterator() {
    return delegate.iterator();
  }

  @Override
  public EventTime appendAndGetAdjustedTime(final EventEnvelope event) throws IOException {
    // if (!sporadicAppendLock.isHeldByCurrentThread()) throw IOException?
    return delegate.appendAndGetAdjustedTime(event);
  }

  @Override
  public boolean appendIfPrevTimeMatch(final EventEnvelope event, final EventTime prevTime)
      throws IOException {
    // if (!sporadicAppendLock.isHeldByCurrentThread()) throw IOException?
    return delegate.appendIfPrevTimeMatch(event, prevTime);
  }

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

  @Override
  public String toString() {
    return this.getClass().getCanonicalName() + "(delegate=" + delegate + ")";
  }
}
