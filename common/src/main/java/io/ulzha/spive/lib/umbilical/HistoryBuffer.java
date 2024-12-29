package io.ulzha.spive.lib.umbilical;

import io.github.resilience4j.circularbuffer.ConcurrentEvictingQueue;
import io.ulzha.spive.lib.EventTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * HeartbeatHistory?
 *
 * <p>History is kept as a best effort, to not overfill storage available on runner, and is not
 * guaranteed to be complete. Should be enough, most of the time, to fill in the gaps that occur
 * when control plane experiences downtime and the runner process outlives it.
 */
public class HistoryBuffer {
  private static class IopwCounter {
    Instant windowStart;
    Instant windowEnd;
    // TODO stall
    long nInputEventsHandledOk;
    long nOutputEvents;
  }

  public static record Iopw(
      Instant windowStart,
      Instant windowEnd,
      // TODO ensure non-lossy JSON serde for longs
      long nInputEventsHandledOk,
      long nOutputEvents) {}

  // TODO let spill to disk?
  // TODO check the concurrent iteration guarantee
  private final ConcurrentEvictingQueue<List<Iopw>> iopws =
      new ConcurrentEvictingQueue<>(4 * 7 * 24 * 60);
  private IopwCounter currIopw = null;

  // TODO evict somehow here too, or perform miraculous compression? to anticipate writes in distant
  // future, far in advance of wall clock
  Queue<Instant> outputEventInstants = new LinkedList<>();

  public void addOutputEvent(EventTime outputEventTime) {
    outputEventInstants.add(outputEventTime.instant);
  }

  /**
   * We so far anchor instance's detailed timeline around the event times recently handled (not wall
   * clock time, not event times being written), and that's what we aggregate into windows for.
   * Numbers are final at that point.
   *
   * <p>TODO sooner, upon reading or successfully emitting first thing beyond current window
   *
   * <p>TODO differently for output-only apps
   *
   * <p>Not thread-safe.
   */
  public void aggregateIopw(Instant instant, long dInputEventsHandledOk) {
    if (currIopw == null) {
      currIopw = new IopwCounter();
      currIopw.windowStart = instant.truncatedTo(ChronoUnit.MINUTES);
      currIopw.windowEnd = currIopw.windowStart.plus(1, ChronoUnit.MINUTES);
    }
    while (currIopw.windowEnd.compareTo(instant) <= 0) {
      while (outputEventInstants.peek() != null
          && outputEventInstants.peek().compareTo(currIopw.windowEnd) < 0) {
        outputEventInstants.poll();
        currIopw.nOutputEvents++;
      }
      iopws.add(
          List.of(
              new Iopw(
                  currIopw.windowStart,
                  currIopw.windowEnd,
                  currIopw.nInputEventsHandledOk,
                  currIopw.nOutputEvents)));
      final IopwCounter nextIopw = new IopwCounter();
      nextIopw.windowStart = currIopw.windowEnd;
      nextIopw.windowEnd = nextIopw.windowStart.plus(1, ChronoUnit.MINUTES);
      currIopw = nextIopw;
    }
    currIopw.nInputEventsHandledOk += dInputEventsHandledOk;
  }

  /** Asynchronously polled by control plane via Runner API. */
  public List<HistoryBuffer.Iopw> getIopwsList(final Instant start) {
    Instant end = null;
    final List<HistoryBuffer.Iopw> list = new ArrayList<>();

    for (var entry : iopws) {
      for (var iopw : entry) {
        if (start == null || iopw.windowEnd().compareTo(start) > 0) {
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
