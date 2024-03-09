package io.ulzha.spive.lib;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Iterator that keeps own output around in memory for fast readback, for cases where output log is
 * also an input. Gathers some additional helper interfaces under one roof.
 *
 * <p>In most respects, delegates to EventLog.AppendIterator.
 *
 * <p>Not thread-safe - event loop and output gateway perform synchronization (when necessary).
 */
// CoordinableEventIterator? LockingEventIterator? EventIteratorChannel vs EventLogChannel?
public class EventIterator implements EventLog.AppendIterator {
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
  public boolean hasNext() {
    if (knownEvents.isEmpty()) {
      return delegate.hasNext();
    } else {
      return true;
    }
  }

  @Override
  public EventEnvelope next() {
    if (knownEvents.isEmpty()) {
      return delegate.next();
    } else {
      return knownEvents.remove();
    }
  }

  /**
   * A bit different docstring than AppendIterator; here {@code next()} is guaranteed to eventually
   * return the appended events, in sequence, after knownEvents are exhausted first.
   */
  @Override
  public EventEnvelope appendOrPeek(final EventEnvelope event) {
    final EventEnvelope actualEvent = delegate.appendOrPeek(event);
    if (actualEvent == event) {
      lastTimeEmitted = event.time();
    }
    if (actualEvent != delegate.next()) { // note side effects
      throw new InternalException(
          "delegate violated appendOrPeek contract - next() should have returned the peeked event");
    }
    knownEvents.add(actualEvent);
    return actualEvent;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + delegate + ")";
  }
}
