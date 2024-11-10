package io.ulzha.spive.app.model.agg;

import io.ulzha.spive.app.events.InstanceIopw;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class Timeline {
  public Timeline() {
    for (Scale scale : Scale.values()) {
      tiles.put(scale, new TreeMap<>());
    }
  }

  public enum Scale {
    SECOND,
    MINUTE,
    HOUR,
    DAY,
    YEAR
  }

  public record Tile(
      // Window in application event time.
      // Lengths currently come from a fixed set:
      // * per second
      // * per minute (used at the default dashboard zoom level, 5 pixels per minute)
      // * per hour
      // * per day (UTC)
      // * per year (grr, inconsistent lengths, exotic calendars)
      // (could generalize into milliseconds or millennia for yet unclear use cases)
      // A wide screen may hold 10ish hours, so we may want to keep minutely windows to cover that.
      // Or maybe that's the job of a cache, platform shall only keep 60... And have ability to
      // reconstruct on demand.
      // Keep minutely for every lagging instance, so catchup progress is visible.
      // Compaction effects... Visible when?
      Instant windowStart, // inclusive
      Instant windowEnd, // exclusive

      // > 0 would mean blurred... For any app with sporadic workloads, the current minute is by
      // definition unknown and constantly has blur?
      long nInputEventsUnknown,

      // all input streams, non-owned and owned (own output that gets read and handled)
      long nInputEventsIncoming,

      // "events in progress" blinking would be just a front-end approximation, rendered from the
      // difference between newest and preceding snapshot of a tile

      long nInputEventsOk,
      long nInputEventsWarning,
      long nInputEventsError,
      long nOutputEvents
      // unsure how output gateway errors would be represented when workloads spontaneously attempt
      // to emit; maps to wall clock time and not really to event time
      ) {}

  public Map<Scale, TreeMap<Instant, Tile>> tiles = new HashMap<>();

  public Instant getMinuteEnd() {
    return getEnd(Scale.MINUTE);
  }

  public Instant getEnd(final Scale scale) {
    if (tiles.get(scale).isEmpty()) {
      return null;
    }
    return tiles.get(scale).lastEntry().getValue().windowEnd();
  }

  public void update(final InstanceIopw iopw) {
    // TODO update - aggregate across multiple input streams
    tiles
        .get(Scale.MINUTE)
        .put(
            iopw.windowStart(),
            new Tile(
                iopw.windowStart(),
                iopw.windowEnd(),
                0,
                0,
                0,
                0,
                iopw.nInputEvents(),
                iopw.nOutputEvents()));
    System.out.println("Minutely scale updated with " + iopw);
  }
}
