package io.ulzha.spive.app.model.agg;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import io.ulzha.spive.app.events.InstanceProgress;

public class Timeline {
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
      int nInputEventsUnknown, // > 0 would mean blurred
      int nInputEventsIncoming,
      // "events in progress" blinking would be just a front-end approximation, rendered from the
      // difference between newest and preceding snapshot of a tile

      int nInputEventsOk,
      int nInputEventsWarning,
      int nInputEventsError,
      int nOutputEvents
      // unsure how output gateway errors would be represented when workloads spontaneously attempt
      // to emit; maps to wall clock time and not really to event time
      ) {}

  public Map<Scale, Map<Instant, Tile>> tiles;

  public void update(InstanceProgress progress) {

    Instant windowStart = progress.checkpoint().instant.truncatedTo(ChronoUnit.MINUTES).minus(1, ChronoUnit.MINUTES);
  }
}
