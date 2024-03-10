package io.ulzha.spive.lib;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

public class EventIteratorTest {
  private EventEnvelope dummyEvent(int i) {
    return new EventEnvelope(
        new EventTime(Instant.ofEpochSecond(1710078716), i), null, "hurr:durr.Batman", null);
  }

  @Test
  public void givenSequenceOfAppendsToEmptyLog_whenReadBack_thenDelegateIsNotAccessed()
      throws Exception {
    final EventEnvelope e0 = dummyEvent(0);
    final EventEnvelope e1 = dummyEvent(1);
    final EventEnvelope e2 = dummyEvent(2);
    try (EventLog log = new InMemoryEventLog()) {
      final EventLog.AppendIterator spy = spy(log.iterator());

      final EventIterator sut = new EventIterator(spy);

      assertTrue(sut.appendOrPeek(e0) == e0);
      assertTrue(sut.appendOrPeek(e1) == e1);
      assertTrue(sut.appendOrPeek(e2) == e2);
      verify(spy, times(3)).appendOrPeek(any());
      verify(spy, times(3)).next();

      assertTrue(sut.next() == e0);
      assertTrue(sut.next() == e1);
      assertTrue(sut.next() == e2);
      verify(spy, times(3)).next(); // no new invocations
    }
  }
}
