package io.ulzha.spive.lib;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/** For testing and debugging. */
public class InMemoryEventLog implements EventLog {
  private LinkedList<EventEnvelope> list = new LinkedList<>();

  @Override
  public EventTime appendAndGetAdjustedTime(final EventEnvelope event) throws IOException {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public synchronized boolean appendIfPrevTimeMatch(
      final EventEnvelope event, final EventTime prevTime) {
    if (event.time().compareTo(prevTime) <= 0) {
      throw new IllegalArgumentException("event time must come strictly after prevTime");
    }

    if (list.isEmpty() && EventTime.INFINITE_PAST.compareTo(prevTime) < 0) {
      throw new IllegalArgumentException(
          "prevTime must be set to INFINITE_PAST, when appending the first event");
    } else if (!list.isEmpty() && list.getLast().time().compareTo(prevTime) < 0) {
      throw new IllegalArgumentException(
          "prevTime must be set to previous event time read, when appending a subsequent event");
    }

    if (!list.isEmpty() && list.getLast().time().compareTo(prevTime) > 0) {
      return false;
    }

    list.addLast(event);
    return true;
  }

  @Override
  public void close() {}

  @Override
  @Nonnull
  public Iterator<EventEnvelope> iterator() {
    return list.iterator();
  }

  /** for testing only */
  public List<Object> asPayloadList() {
    return list.stream()
        .map(event -> Type.fromTypeTag(event.typeTag()).deserialize(event.serializedPayload()))
        .collect(Collectors.toList());
  }

  /** for testing only */
  public EventTime latestEventTime() {
    if (list.isEmpty()) {
      return EventTime.INFINITE_PAST;
    } else {
      return list.getLast().time();
    }
  }
}
