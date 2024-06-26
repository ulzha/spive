package io.ulzha.spive.lib;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * Iterator that keeps own output around in memory for fast readback, for cases where output log is
 * also an input. Gathers some additional helper interfaces under one roof.
 *
 * <p>In most respects, delegates to EventLog.AppendIterator.
 *
 * <p>The appendOrPeek method is thread-safe, and would wake up hasNext() resp. next() if they were
 * blocked.
 */
// CoordinableEventIterator? LockingEventIterator? EventIteratorChannel vs EventLogChannel?
// EventLog.ConcurrentAppendIterator? Instance.ConcurrentAppendIterator and Instance.Main?
// lib.instance? dist?
// Yet another layer to come, for multiple inputs, and watermark awareness?
public class EventIterator implements Iterator<EventEnvelope> {
  private final EventLog.AppendIterator delegate;
  // may accrue 1 event that's been just emitted by a sporadic workload, or alternatively n
  // consecutive events that have been emitted (deterministically, as direct or indirect
  // consequences of a preceding event) by event handlers
  private final Queue<EventEnvelope> knownEvents = new LinkedList<>();

  public EventTime lastTimeRead = EventTime.INFINITE_PAST;
  public EventTime lastTimeEmitted = EventTime.INFINITE_PAST;

  public EventIterator(final EventLog.AppendIterator delegate) {
    this.delegate = delegate;
  }

  @Override
  public synchronized boolean hasNext() {
    while (knownEvents.isEmpty() && delegate.wouldBlock()) {
      System.out.println("In snazzy wait loop...");
      try {
        wait(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    if (knownEvents.isEmpty()) {
      return delegate.hasNext();
    } else {
      return true;
    }
  }

  @Override
  public synchronized EventEnvelope next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    final EventEnvelope event;

    if (knownEvents.isEmpty()) {
      event = delegate.next();
    } else {
      event = knownEvents.remove();
    }

    lastTimeRead = event.time();
    return event;
  }

  /**
   * A bit different docstring than AppendIterator; here {@code next()} is guaranteed to eventually
   * return the appended events, in sequence, after knownEvents are exhausted first.
   */
  public synchronized EventEnvelope appendOrPeek(final EventEnvelope event) {
    final EventEnvelope actualEvent = delegate.appendOrPeek(event);
    if (actualEvent == event) {
      lastTimeEmitted = event.time();
    }
    if (actualEvent != delegate.next()) { // note side effects
      throw new InternalException(
          "delegate violated appendOrPeek contract - next() should have returned the peeked event");
    }
    knownEvents.add(actualEvent);
    notify();
    return actualEvent;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + delegate + ")";
  }
}
