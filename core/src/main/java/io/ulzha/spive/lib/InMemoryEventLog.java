package io.ulzha.spive.lib;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** For testing and debugging. */
public class InMemoryEventLog implements EventLog {
  private List<EventEnvelope> list = new ArrayList<>();

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
    } else if (!list.isEmpty() && list.get(list.size() - 1).time().compareTo(prevTime) < 0) {
      throw new IllegalArgumentException(
          "prevTime must be set to previous event time read, when appending a subsequent event");
    }

    if (!list.isEmpty() && list.get(list.size() - 1).time().compareTo(prevTime) > 0) {
      return false;
    }

    list.add(event);
    return true;
  }

  @Override
  public void close() {}

  @Override
  @Nonnull
  public AppendIterator iterator() {
    return new AppendIteratorImpl();
  }

  private class AppendIteratorImpl implements AppendIterator {
    private int i = 0;

    @Override
    public boolean wouldBlock() {
      // FIXME
      return false;
    }

    @Override
    public boolean hasNext() {
      // FIXME block and null terminate
      return i < list.size();
    }

    @Override
    public EventEnvelope next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return list.get(i++);
    }

    @Override
    public EventEnvelope appendOrPeek(EventEnvelope event) {
      final EventTime prevTime = (i == 0 ? EventTime.INFINITE_PAST : list.get(i - 1).time());
      if (InMemoryEventLog.this.appendIfPrevTimeMatch(event, prevTime)) {
        return event;
      } else {
        return list.get(i);
      }
    }
  }

  /** for testing only */
  public List<Object> asPayloadList() {
    return list.stream()
        .map(event -> EventSerde.forTypeTag(event.typeTag()).deserialize(event.serializedPayload()))
        .collect(Collectors.toList());
  }

  /** for testing only */
  public EventTime latestEventTime() {
    if (list.isEmpty()) {
      return EventTime.INFINITE_PAST;
    } else {
      return list.get(list.size() - 1).time();
    }
  }
}
