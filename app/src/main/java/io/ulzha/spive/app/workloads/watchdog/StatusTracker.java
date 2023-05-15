package io.ulzha.spive.app.workloads.watchdog;

import static io.ulzha.spive.lib.umbilical.UmbilicalReader.getErrorUpdate;
import static io.ulzha.spive.lib.umbilical.UmbilicalReader.getTimeoutUpdate;
import static io.ulzha.spive.lib.umbilical.UmbilicalReader.isError;
import static io.ulzha.spive.lib.umbilical.UmbilicalReader.isTimeout;

import io.ulzha.spive.app.events.InstanceStatusChange;
import io.ulzha.spive.app.model.InstanceStatus;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import io.ulzha.spive.lib.umbilical.UmbilicalReader;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Optimized to only iterate forward in the underlying NavigableMap, and not scan heartbeat history
 * repeatedly.
 *
 * <p>Not thread-safe.
 */
public class StatusTracker {
  private final UmbilicalReader placenta;
  private EventTime currentEventTime = EventTime.INFINITE_PAST;
  private Supplier<Instant> wallClockTime;
  private final InstanceStatusChange.Draft event;

  public StatusTracker(
      final UmbilicalReader placenta,
      final Supplier<Instant> wallClockTime,
      final UUID instanceId) {
    this.placenta = placenta;
    this.wallClockTime = wallClockTime;
    this.event = new InstanceStatusChange.Draft();
    this.event.instanceId = instanceId;
    this.event.status = InstanceStatus.NOMINAL.name();
  }

  /**
   * Regarding timeout, we report it only if we have caught the moment when the current event is
   * overdue, and otherwise report nominal, as a "recovery" at unknown instant. Could be improved.
   */
  public InstanceStatusChange getStatus(int timeoutMillis) {
    if (event.status.equals(InstanceStatus.ERROR.name())) {
      return new InstanceStatusChange(event);
    }

    EventTime nextEventTime = placenta.getNextEventTime(currentEventTime);

    while (nextEventTime != null) {
      currentEventTime = nextEventTime;
      if (isError(placenta.get(currentEventTime))) {
        event.eventTime = currentEventTime;
        event.status = InstanceStatus.ERROR.name();
        final ProgressUpdate update = getErrorUpdate(placenta.get(currentEventTime));
        event.instant = update.instant();
        event.cause = update.error();
        // Sticks to the same return record for the remainder of the poll loop.
        return new InstanceStatusChange(event);
      }
      nextEventTime = placenta.getNextEventTime(currentEventTime);
    }

    if (currentEventTime != EventTime.INFINITE_PAST) {
      if (isTimeout(placenta.get(currentEventTime), timeoutMillis, wallClockTime.get())) {
        event.eventTime = currentEventTime;
        event.status = InstanceStatus.TIMEOUT.name();
        final ProgressUpdate update =
            getTimeoutUpdate(placenta.get(currentEventTime), timeoutMillis);
        event.instant = update.instant();
        event.cause = update.warning();
      } else {
        // recovery, at unknown instant, with unknown cause
        event.eventTime = currentEventTime;
        event.status = InstanceStatus.NOMINAL.name();
        event.instant = null;
        event.cause = null;
      }
    }

    //    final InstanceStatusChange instanceStatusChange = initNominal();
    //    if (!findFirstError(response, instanceStatusChange)) {
    //      // TODO && !findExit()... Signal as a success update on the null event?
    //      if (findFirstTimeout(response, instanceStatusChange)) {
    //        // If the latest event was on time then we opt to not even register an intermittent
    //        // timeout. If desired, then ensure the truncation on runner preserves those, too...
    //        findLastRecovery(response, instanceStatusChange);
    //      }
    //    }

    return new InstanceStatusChange(event);
  }
}
