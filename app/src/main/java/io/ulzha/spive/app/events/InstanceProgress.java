package io.ulzha.spive.app.events;

import io.ulzha.spive.lib.EventTime;
import java.util.UUID;

/**
 * Captures offsets from the event loop, with reasonably coarse granularity.
 *
 * <p>The latest checkpoint persisted is used to make a replay schedule (prevent duplicate side
 * effects up to and including checkpoint) when the instance needs to be sharded or repaired.
 *
 * <p>The event counts contribute to exact aggregated numbers for event volume visualization in UI.
 *
 * <p>An InstanceProgress event (unlike InstanceStatusChange) is spawned to describe every instance
 * on a regular basis, meaning approximately regularly in wall clock time.
 *
 * <p>(Will benefit from compaction.)
 */
public record InstanceProgress(
    UUID instanceId, // partition key
    EventTime checkpoint,
    long nInputEventsPreviousWindow, // count for the minute that precedes checkpoint
    // (eventually reformulate in terms of watermark?)
    long nInputEventsTotal, // count since start of instance's lifetime until checkpoint, inclusive
    long nOutputEventsPreviousWindow,
    long nOutputEventsTotal

    // TODO byte[] sparkline... Compact individual timings perhaps... Or a separate channel for that
    ) {}
