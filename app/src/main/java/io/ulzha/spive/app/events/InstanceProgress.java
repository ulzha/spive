package io.ulzha.spive.app.events;

import io.ulzha.spive.lib.EventTime;
import java.util.UUID;

/**
 * Captures offsets from the event loop, with reasonably coarse granularity.
 *
 * <p>The latest checkpoint persisted is used to make a replay schedule (prevent duplicate side
 * effects up to and including checkpoint) when the instance needs to be sharded or repaired.
 *
 * <p>(Will benefit from compaction.)
 */
public record InstanceProgress(
    UUID instanceId, // partition key
    EventTime checkpoint
    // TODO byte[] sparkline
    ) {}
