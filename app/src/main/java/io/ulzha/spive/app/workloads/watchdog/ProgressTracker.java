package io.ulzha.spive.app.workloads.watchdog;

import static io.ulzha.spive.lib.umbilical.UmbilicalReader.isSuccess;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.UmbilicalReader;

/**
 * Optimized to only iterate forward in the underlying NavigableMap, and not scan heartbeat history
 * repeatedly.
 *
 * <p>Not thread-safe.
 */
public class ProgressTracker {
  private final UmbilicalReader placenta;
  private EventTime currentEventTime = EventTime.INFINITE_PAST;

  public ProgressTracker(final UmbilicalReader placenta) {
    this.placenta = placenta;
  }

  public EventTime getCheckpoint() {
    EventTime nextEventTime = placenta.getNextEventTime(currentEventTime);

    while (nextEventTime != null && isSuccess(placenta.get(nextEventTime))) {
      currentEventTime = nextEventTime;
      nextEventTime = placenta.getNextEventTime(currentEventTime);
    }

    return currentEventTime;
  }
}
