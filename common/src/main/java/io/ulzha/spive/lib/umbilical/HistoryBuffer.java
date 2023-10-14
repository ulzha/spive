package io.ulzha.spive.lib.umbilical;

import io.github.resilience4j.circularbuffer.ConcurrentEvictingQueue;
import io.ulzha.spive.lib.EventTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * HeartbeatHistory?
 *
 * <p>History is kept as a best effort, to not overfill storage available, and is not guaranteed to
 * be complete. Should be enough, most of the time, to fill in the gaps that occur when control
 * plane experiences downtime and the runner process outlives it.
 */
public class HistoryBuffer {
  private static class IopwCounter {
    Instant windowStart;
    Instant windowEnd;
    long nInputEvents;
    long nOutputEvents;
  }

  public static record Iopw(
      Instant windowStart, Instant windowEnd, long nInputEvents, long nOutputEvents) {}

  // TODO let spill to disk, and cap
  // TODO check the concurrent iteration guarantee
  private final ConcurrentEvictingQueue<List<Iopw>> iopws =
      new ConcurrentEvictingQueue<>(4 * 7 * 24 * 60);
  private IopwCounter currIopw = null;

  public void aggregateIopw(EventTime eventTime, long dInputEvents, long dOutputEvents) {
    if (currIopw == null || eventTime.instant.compareTo(currIopw.windowEnd) > 0) {
      if (currIopw != null) {
        iopws.add(
            List.of(
                new Iopw(
                    currIopw.windowStart,
                    currIopw.windowEnd,
                    currIopw.nInputEvents,
                    currIopw.nOutputEvents)));
        // TODO coarser granularities as long as we know them
      }
      currIopw = new IopwCounter();
      currIopw.windowStart = eventTime.instant.truncatedTo(ChronoUnit.MINUTES);
      currIopw.windowEnd = currIopw.windowStart.plus(1, ChronoUnit.MINUTES);
    }
    currIopw.nInputEvents += dInputEvents;
    currIopw.nOutputEvents += dOutputEvents;
  }

  /** Asynchronously polled by control plane via Runner API. */
  public List<HistoryBuffer.Iopw> getIopwsList(final Instant start) {
    Instant end = null;
    final List<HistoryBuffer.Iopw> list = new ArrayList<>();

    for (var entry : iopws) {
      for (var iopw : entry) {
        if (iopw.windowEnd().compareTo(start) > 0) {
          if (end == null) {
            end = iopw.windowEnd().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
          }
          if (iopw.windowEnd().compareTo(end) <= 0) {
            list.add(iopw);
          } else {
            return list;
          }
        }
      }
    }
    return list;
  }
}
