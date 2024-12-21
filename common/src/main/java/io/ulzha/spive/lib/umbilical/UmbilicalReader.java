package io.ulzha.spive.lib.umbilical;

import io.ulzha.spive.lib.EventTime;
import java.time.Instant;
import java.util.List;

public interface UmbilicalReader {
  HeartbeatSnapshot updateHeartbeat() throws InterruptedException;

  List<ProgressUpdate> get(EventTime t);

  EventTime getNextEventTime(EventTime t);

  List<HistoryBuffer.Iopw> updateIopws(Instant start) throws InterruptedException;

  // should refactor these away to use explicit indicators structure (typed and consistent) from
  // runner. Pretending we lose it and re-interpreting the sample is premature/pointless
  static boolean isSuccess(List<ProgressUpdate> updates) {
    for (var update : updates) {
      if (update.success()) {
        return true;
      }
    }
    return false;
  }

  static boolean isError(List<ProgressUpdate> updates) {
    for (var update : updates) {
      if (update.error() != null) {
        return true;
      }
    }
    return false;
  }

  // overdue
  static boolean isStall(List<ProgressUpdate> updates, int timeoutMillis, Instant now) {
    return !isSuccess(updates)
        && !isError(updates)
        && updates.get(0).instant().plusMillis(timeoutMillis).isBefore(now);
  }

  /**
   * @return the first error
   */
  static ProgressUpdate getErrorUpdate(List<ProgressUpdate> updates) {
    for (var update : updates) {
      if (update.error() != null) {
        return update;
      }
    }
    throw new IllegalArgumentException("list contains no error");
  }

  /**
   * @return computed stall instant and the first warning before that, if any
   */
  static ProgressUpdate getStallUpdate(List<ProgressUpdate> updates, int timeoutMillis) {
    if (updates.size() == 0) {
      throw new IllegalArgumentException("list is empty");
    }
    final Instant stallInstant = updates.get(0).instant().plusMillis(timeoutMillis);
    String firstWarning = null;
    for (var update : updates) {
      if (!update.instant().isBefore(stallInstant)) {
        break;
      }
      if (update.warning() != null) {
        firstWarning = update.warning();
        break;
      }
    }
    return new ProgressUpdate(stallInstant, false, firstWarning, null);
  }
}
