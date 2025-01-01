package io.ulzha.spive.basicrunner.api;

import static java.util.Comparator.*;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.ConcurrentProgressUpdatesList;
import io.ulzha.spive.lib.umbilical.HeartbeatSnapshot;
import io.ulzha.spive.lib.umbilical.HistoryBuffer;
import io.ulzha.spive.lib.umbilical.HistoryBuffer.Iopw;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import io.ulzha.spive.lib.umbilical.ProgressUpdatesList;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Facilitates the tiny bit of asynchronous bidirectional communication needed between control plane
 * and a process instance.
 *
 * <p>Designed to act as a sort of a sliding-window buffer in order to minimize need for near-real
 * time communication and gracefully handle some lag. Replay mode toggling is scheduled into the
 * near future, while heartbeats and errors are buffered over the recent past as a best effort.
 * (Error _presence_ accounting is not sacrificed at any point, only debugging information may be.)
 *
 * <p>Comprehensive high volume log collection is outside of Spīve's scope. It is just a boon for
 * productivity if the platform UI also keeps at least a sample of failure stacktraces available at
 * our fingertips for troubleshooting purposes.
 *
 * <p>Control of the replay mode must err towards correctness. (An instance may toggle replay off
 * and cause repeated side effects, but it must never drop into replay mode inadvertently, as that
 * would cause dropped side effects.)
 *
 * <p>Thread-safety is limited: allows snapshotting from API thread(s), concurrently with updates
 * through Umbilicus.
 *
 * <p>(Ugly. Wonder if we can hide BasicRunnerClient and Umbilicus, make it just Umbilical on both
 * ends...)
 */
public class Umbilical {

  private SortedMap<EventTime, Boolean> replayModeSchedule = new ConcurrentSkipListMap<>();
  // TODO trace the latest event handler, to know how long it's blocking and on which gateway call
  // TODO checkpoint and report (at least) the first failure _per partition_, so UI shows exactly
  // where blockers start, and SpiveScaler can create a dedicated instance, in case they're not
  // overwhelmingly many
  private final Heartbeat heartbeat = new Heartbeat();
  // TODO pass start time and expect iopws contiguous from there (if not ops provided, then infer
  // from stream metadata or watermark?)
  private final HistoryBuffer buffer = new HistoryBuffer();
  // TODO separate sample for each workload, and dedicated methods. This mishmash will get confusing
  private final AtomicReference<EventTime> firstErrorEventTime = new AtomicReference<>();
  // Used for deduplicating, to not re-report the fatal error needlessly upon instance teardown.
  // Unneeded?
  private final AtomicReference<Throwable> lastError = new AtomicReference<>();
  // TODO report when we're trailing
  // TODO will probably benefit from a few more configuration items from SpiveScaler:
  //  * handlerTimeout - same for all handlers, or perhaps per gateway?
  //  * throttle
  //  * trailerLease - or is this superseded by replayModeSchedule?

  /**
   * Set by control plane via Runner API.
   *
   * <p>Always overwrites the whole schedule.
   */
  public void setReplayModeSchedule(SortedMap<EventTime, Boolean> schedule) {
    // TODO assert that settings don't get changed retroactively?
    // Or/and authenticate caller as Spive and only then thrust?
    replayModeSchedule = schedule;
  }

  /**
   * (true = replay without side effects, false = do perform side effects) Asynchronously polled by
   * instance.
   *
   * @return the replay mode applicable at eventTime according to the currently known schedule.
   */
  public boolean getReplayMode(EventTime eventTime) {
    for (Map.Entry<EventTime, Boolean> entry : replayModeSchedule.entrySet()) {
      if (eventTime.compareTo(entry.getKey()) >= 0) {
        return entry.getValue();
      }
    }
    return false;
  }

  /** Set by event loop before every event handled. */
  public void addHeartbeat(EventTime eventTime) {
    addHeartbeat(eventTime, new ProgressUpdate());
  }

  /** Asynchronously polled by control plane via Runner API. */
  public HeartbeatSnapshot getHeartbeatSnapshot(boolean verbose) {
    List<ProgressUpdatesList> sample = new ArrayList<>();

    synchronized (heartbeat) {
      for (var entry : heartbeat.entryListSnapshot()) {
        sample.add(new ProgressUpdatesList(entry.getKey(), null, entry.getValue().toList()));
      }
      if (!verbose) {
        sample = getFirsts(sample);
      }

      return new HeartbeatSnapshot(
          sample,
          getLastHandledEventTime(sample),
          heartbeat.nInputEventsHandled,
          heartbeat.nOutputEvents);
    }
  }

  /**
   * Helper to deverbosify response.
   *
   * @return a filtered map containing only entries for the latest success (if any), the first
   *     warning (if any), the first error (if any), and the latest event time. The failure messages
   *     are truncated to the first line.
   */
  public static List<ProgressUpdatesList> getFirsts(final List<ProgressUpdatesList> sample) {
    final List<ProgressUpdatesList> firsts = new ArrayList<>();
    ProgressUpdatesList lastSuccess = null;
    ProgressUpdatesList firstWarning = null;
    ProgressUpdatesList firstError = null;
    ProgressUpdatesList last = null;

    for (var list : sample) {
      for (var update : list.progressUpdates()) {
        if (update.success()) {
          lastSuccess = list;
        }
        if (update.warning() != null && firstWarning == null) {
          firstWarning = list;
        }
        if (update.error() != null && firstError == null) {
          firstError = list;
          // currently nothing can follow, but TODO elaborate when multiple erroring partitions
        }
      }
      last = list;
    }

    if (lastSuccess != null) {
      firsts.add(lastSuccess);
    }
    if (firstWarning != null && !firsts.contains(firstWarning)) {
      firsts.add(firstWarning);
    }
    if (firstError != null && !firsts.contains(firstError)) {
      firsts.add(firstError);
    }
    if (last != null && !firsts.contains(last)) {
      firsts.add(last);
    }
    // System.out.println(firsts);
    // firsts.sort((a, b) -> a.eventTime().compareTo(b.eventTime()));
    firsts.sort(comparing(ProgressUpdatesList::eventTime, nullsFirst(naturalOrder())));

    firsts.replaceAll(
        list -> {
          list.progressUpdates()
              .replaceAll(
                  progressUpdate ->
                      new ProgressUpdate(
                          progressUpdate.instant(),
                          progressUpdate.success(),
                          truncateStacktrace(progressUpdate.warning()),
                          truncateStacktrace(progressUpdate.error())));
          return list;
        });

    return firsts;
  }

  /**
   * Helper to make predictable heartbeat sizes.
   *
   * <p>TODO allow a few k, just protect against stackoverflow. Peel off
   * java.util.concurrent.ExecutionException and Caused by:
   * java.lang.reflect.InvocationTargetException and potentially java.lang.RuntimeException
   * (especially "RuntimeException: Application failure", and all the other cruft that wraps
   * InvocationTargetException in EventLoop... InterruptedException also interesting)
   *
   * <p>Eventually, with firsts per partition, multiple stacktraces may have to fit in one
   * heartbeat.
   */
  private static @Nullable String truncateStacktrace(final @Nullable String stacktrace) {
    if (stacktrace == null) {
      return stacktrace;
    }

    int iBreak = stacktrace.indexOf('\n');

    return (iBreak == -1 ? stacktrace : stacktrace.substring(0, iBreak));
  }

  /**
   * Helper to deverbosify response.
   *
   * @return the last successfully handled event time
   */
  public static @Nullable EventTime getLastHandledEventTime(
      final List<ProgressUpdatesList> sample) {
    for (int i = sample.size() - 1; i >= 0; i--) {
      for (ProgressUpdate update : sample.get(i).progressUpdates()) {
        if (update.success()) {
          return sample.get(i).eventTime();
        }
      }
    }
    return null;
  }

  // TODO SSE tunnel of some sort, so UI can stream directly from the runner, with minimal delay on
  // focus?
  public class Umbilicus implements UmbilicalWriter {
    public final Supplier<EventTime> currentEventTime;

    public Umbilicus(Supplier<EventTime> currentEventTime) {
      this.currentEventTime = currentEventTime;
    }

    public boolean getReplayMode() {
      return Umbilical.this.getReplayMode(currentEventTime.get());
    }

    public void addSuccess() {
      Umbilical.this.addSuccess(currentEventTime.get());
    }

    /** Set on retryable failures. */
    public void addWarning(Throwable warning) {
      Umbilical.this.addWarning(currentEventTime.get(), warning);
    }

    /** Set on non-retryable failures. */
    public void addError(Throwable error) {
      Umbilical.this.addError(currentEventTime.get(), error);
    }

    @Override
    public void addHeartbeat() {
      Umbilical.this.addHeartbeat(currentEventTime.get());
    }

    @Override
    /**
     * Not thread-safe. (Currently we only have output gateway of the locking variety, it takes care
     * of coordinating updates between event loop and concurrent workloads.)
     */
    public void addOutputEvent(EventTime outputEventTime) {
      Umbilical.this.addOutputEvent(outputEventTime);
    }
  }

  /** Set by event loop after every event successfully handled. */
  public void addSuccess(EventTime eventTime) {
    addHeartbeat(eventTime, ProgressUpdate.createSuccess());
  }

  public void addWarning(EventTime eventTime, Throwable warning) {
    addHeartbeat(eventTime, ProgressUpdate.createWarning(warning));
  }

  public boolean addError(EventTime eventTime, Throwable error) {
    if (eventTime != null) {
      firstErrorEventTime.compareAndSet(null, eventTime);
    }
    if (lastError.getAndSet(error) == error) {
      return false;
    } else {
      addHeartbeat(eventTime, ProgressUpdate.createError(error));
      return true;
    }
  }

  private void addHeartbeat(EventTime eventTime, ProgressUpdate update) {
    // after start of processing each event, updates list must appear consistently nonempty
    // after success, input count must appear consistently incremented
    // after emitting each event, output count must appear uncontradictory with input count
    // at other times, the updates can be added with less contention
    // nevertheless we could maintain order, make heartbeat receive updates first
    final ConcurrentProgressUpdatesList list = heartbeat.get(eventTime);
    if (list == null) {
      synchronized (heartbeat) {
        heartbeat.truncate(firstErrorEventTime.get());
        heartbeat.put(eventTime, new ConcurrentProgressUpdatesList(update));
      }
    } else if (update.success()) {
      synchronized (heartbeat) {
        list.add(update);
        heartbeat.nInputEventsHandled++;
        buffer.aggregateIopw(eventTime.instant, 1);
      }
    } else {
      list.add(update);
    }
  }

  /**
   * Outbound bookkeeping helper, to allow thread-safe snapshotting by API thread.
   *
   * <p>Tracks event time and stats, and the progress updates during each event, in constant space.
   *
   * <p>TODO maintain an even more elaborate sample - the first warning and the eventual error per
   * partition? firsts for distinct warnings? and counts? same eventually for errors
   */
  private static class Heartbeat {
    // ConcurrentSkipListMap doesn't support null keys, but we maintain a convention that null means
    // event time is unknown/not applicable
    AtomicReference<ConcurrentProgressUpdatesList> unknownEventTimeSample = new AtomicReference<>();
    private final SortedMap<EventTime, ConcurrentProgressUpdatesList> sample =
        new ConcurrentSkipListMap<>();
    public long nInputEventsHandled;
    public long nOutputEvents;

    private void truncate(final EventTime eventTimeToKeep) {
      if (sample.size() > 9 || unknownEventTimeSample.get() != null && sample.size() == 9) {
        // Pick something to truncate, but avoid entries that may be of particular interest:
        // * the null eventTime (heartbeat before the first event was read, alt. between events)
        // * the latest successful event
        // * the first slow event (? maybe sampled slowness is of interest)
        // * the first event with a warning (? maybe recent warnings are of interest)
        // * the first event with an error
        // * the latest event
        // TODO compute correctly and give proper types; probably precompute in heartbeatFirsts, so
        // truncating is less loopy and message sizes are reduced
        final var iterator = sample.entrySet().iterator();
        while (iterator.hasNext()) {
          final var entry = iterator.next();
          if (entry.getKey() != eventTimeToKeep && iterator.hasNext()) {
            sample.remove(entry.getKey(), entry.getValue());
            break;
          }
        }
      }
    }

    private ConcurrentProgressUpdatesList get(final EventTime eventTime) {
      if (eventTime == null) {
        return unknownEventTimeSample.get();
      }
      return sample.get(eventTime);
    }

    private ConcurrentProgressUpdatesList put(
        final EventTime eventTime, final ConcurrentProgressUpdatesList value) {
      if (eventTime == null) {
        return unknownEventTimeSample.getAndSet(value);
      }
      return sample.put(eventTime, value);
    }

    /**
     * Snapshot with "weak consistency" as documented in java.util.concurrent package.
     *
     * <p>Nulls first, and EventTimes in increasing order later.
     */
    private List<Map.Entry<EventTime, ConcurrentProgressUpdatesList>> entryListSnapshot() {
      final List<Map.Entry<EventTime, ConcurrentProgressUpdatesList>> list = new ArrayList<>();
      final ConcurrentProgressUpdatesList unknownEventTimeValue = unknownEventTimeSample.get();
      if (unknownEventTimeValue != null) {
        list.add(new AbstractMap.SimpleEntry<>(null, unknownEventTimeValue));
      }
      for (Map.Entry<EventTime, ConcurrentProgressUpdatesList> entry : sample.entrySet()) {
        list.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
      }
      return list;
    }
  }

  public void addOutputEvent(EventTime outputEventTime) {
    synchronized (heartbeat) {
      heartbeat.nOutputEvents++;
    }
    buffer.addOutputEvent(outputEventTime);
  }

  public void aggregateIopw(Instant instant, long dInputEventsHandled) {
    buffer.aggregateIopw(instant, dInputEventsHandled);
  }

  public List<Iopw> getIopwsList(final Instant start) {
    return buffer.getIopwsList(start);
  }

  // TODO report performance statistics
  // TODO report hot key sets up (or heavy... costly by any attribute)
  // TODO UDP likely appropriate

  // Does this have to be in the runner? Perhaps the event loop should expose a snoop endpoint
  // instead?
  // Or perhaps runner memory is slightly more persistent in face of instance crashes, and that's
  // good
}
