package io.ulzha.spive.tools;

import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventLog;
import io.ulzha.spive.lib.EventTime;

/** A maintainer's tool for naively copying raw logs. */
public class CopyEventLog {
  public static void main(final String... args) throws Exception {
    final EventLog src = EventLog.open(args[0], args[1]);
    final EventLog dst = EventLog.open(args[2], args[3]);
    final EventTime last = EventTime.fromString(args[4]);

    EventTime prevTime = EventTime.INFINITE_PAST;
    // FIXME in this context we probably want to throw if it ends unfinalized and before `last`
    // (start.sh with #3 tricked me), or really get rid of `last` and use ranges and throw
    for (EventEnvelope event : src) {
      dst.appendIfPrevTimeMatch(event, prevTime);
      prevTime = event.time();
      if (event.time().equals(last)) break;
    }
  }
}
