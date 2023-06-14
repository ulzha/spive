package io.ulzha.spive.basicrunner.api;

import io.github.resilience4j.circularbuffer.ConcurrentEvictingQueue;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSample;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import jakarta.annotation.Nullable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

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
 * <p>Methods are thread-safe.
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
  public List<HeartbeatSample> getHeartbeatSnapshot() {
    final List<HeartbeatSample> heartbeatSnapshot = new ArrayList<>();

    for (var entry : heartbeat.entryListSnapshot()) {
      heartbeatSnapshot.add(new HeartbeatSample(entry.getKey(), null, entry.getValue().toList()));
    }

    return heartbeatSnapshot;
  }

  /**
   * Helper to deverbosify response.
   *
   * @return a filtered map containing only entries for the first warning (if any), the first error
   *     (if any), and the latest event time. The failure messages are truncated to the first line.
   */
  public static List<HeartbeatSample> getFirsts(final List<HeartbeatSample> heartbeatSnapshot) {
    final List<HeartbeatSample> firsts = new ArrayList<>();
    HeartbeatSample firstWarning = null;
    HeartbeatSample firstError = null;
    HeartbeatSample lastSample = null;

    for (var sample : heartbeatSnapshot) {
      for (var update : sample.progressUpdates()) {
        boolean added = false; // just to prevent duplicates in the returned list
        if (update.warning() != null && firstWarning == null) {
          firstWarning = sample;
          firsts.add(sample);
          added = true;
        }
        if (update.error() != null && firstError == null) {
          firstError = sample;
          if (!added) {
            firsts.add(sample);
          }
        }
      }
      lastSample = sample;
    }

    if (lastSample != null && lastSample != firstWarning && lastSample != firstError) {
      firsts.add(lastSample);
    }

    firsts.replaceAll(
        sample -> {
          sample
              .progressUpdates()
              .replaceAll(
                  progressUpdate ->
                      new ProgressUpdate(
                          progressUpdate.instant(),
                          progressUpdate.success(),
                          truncateStacktrace(progressUpdate.warning()),
                          truncateStacktrace(progressUpdate.error())));
          return sample;
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
      final List<HeartbeatSample> heartbeatSnapshot) {
    for (int i = heartbeatSnapshot.size() - 1; i >= 0; i--) {
      final List<ProgressUpdate> progressUpdates = heartbeatSnapshot.get(i).progressUpdates();
      for (ProgressUpdate update : progressUpdates) {
        if (update.success()) {
          return heartbeatSnapshot.get(i).eventTime();
        }
      }
    }
    return null;
  }

  // TODO SSE tunnel of some sort, so UI can stream directly from the runner, with minimal delay on
  // focus?
  public class Umbilicus implements UmbilicalWriter {
    public final AtomicReference<EventTime> currentEventTime;

    public Umbilicus(AtomicReference<EventTime> currentEventTime) {
      this.currentEventTime = currentEventTime;
    }

    public boolean getReplayMode() {
      return Umbilical.this.getReplayMode(currentEventTime.get());
    }

    /** Set on retryable failures. */
    public void addWarning(Throwable warning) {
      Umbilical.this.addWarning(currentEventTime.get(), warning);
    }

    /** Set on non-retryable failures. */
    public void addError(Throwable error) {
      Umbilical.this.addError(currentEventTime.get(), error);
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
    heartbeat.truncate(firstErrorEventTime.get());
    final ConcurrentProgressUpdatesList list =
        heartbeat.computeIfAbsent(eventTime, keyIgnored -> new ConcurrentProgressUpdatesList());
    list.add(update);
  }

  /**
   * A thread safe bookkeeping helper, tracking event times encountered, and the progress updates
   * for each.
   *
   * <p>TODO maintain an even more elaborate sample - the first warning and the eventual error per
   * partition? Perhaps also keep count of truncated samples.
   */
  private static class Heartbeat {
    // ConcurrentSkipListMap doesn't support null keys, but we maintain a convention that null means
    // event time is unknown/not applicable
    AtomicReference<ConcurrentProgressUpdatesList> unknownEventTimeSample = new AtomicReference<>();
    private final SortedMap<EventTime, ConcurrentProgressUpdatesList> sample =
        new ConcurrentSkipListMap<>();

    private void truncate(final EventTime eventTimeToKeep) {
      if (sample.size() >= 10) { // TODO less racy
        // Pick something to truncate, but avoid entries that may be of particular interest:
        // * the null eventTime (workload heartbeat before the first event was read)
        // * the first slow event (? maybe sampled slowness is of interest)
        // * the first event with a warning (? maybe recent warnings are of interest)
        // * the first event with an error
        // * the latest event
        // TODO compute correctly; probably precompute in heartbeatFirsts, so truncating is less
        // loopy and message sizes are reduced
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

    private ConcurrentProgressUpdatesList computeIfAbsent(
        final EventTime eventTime,
        Function<EventTime, ConcurrentProgressUpdatesList> mappingFunction) {
      if (eventTime == null) {
        if (unknownEventTimeSample.get() == null) {
          final ConcurrentProgressUpdatesList list = mappingFunction.apply(null);
          unknownEventTimeSample.compareAndSet(null, list);
        }
        return unknownEventTimeSample.get();
      }
      return sample.computeIfAbsent(eventTime, mappingFunction);
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

  /**
   * A bit like ConcurrentEvictingQueue<ProgressUpdate> but aware of firsts to keep.
   *
   * <p>TODO Also should detect contradictory sequences, like a failure before handler start
   * instant, or a failure after handler end (workloads ATTOW intersperse updates with handler
   * updates). Safeguard against clock weirdness causing misinterpretation.
   */
  private static class ConcurrentProgressUpdatesList {
    AtomicReference<ProgressUpdate> start = new AtomicReference<>();
    AtomicReference<ProgressUpdate> firstError = new AtomicReference<>();
    ConcurrentEvictingQueue<ProgressUpdate> rest = new ConcurrentEvictingQueue<>(3);

    void add(ProgressUpdate update) {
      if (update.error() == null && update.warning() == null) {
        if (!start.compareAndSet(null, update)) {
          rest.add(update);
        }
      } else if (update.error() != null) {
        if (!firstError.compareAndSet(null, update)) {
          rest.add(update);
        }
      } else {
        rest.add(update);
      }
    }

    List<ProgressUpdate> toList() {
      Set<ProgressUpdate> allUpdates = new HashSet<>();
      allUpdates.add(start.get());
      allUpdates.add(firstError.get());
      allUpdates.addAll(rest);
      return allUpdates.stream()
          .filter(Objects::nonNull)
          .sorted(Comparator.comparing(ProgressUpdate::instant))
          .collect(Collectors.toList());
    }
  }

  // TODO report performance statistics
  // TODO report hot key sets up (or heavy... costly by any attribute)
  // TODO UDP likely appropriate

  // Does this have to be in the runner? Perhaps the event loop should expose a snoop endpoint
  // instead?
  // Or perhaps runner memory is slightly more persistent in face of instance crashes, and that's
  // good
}
