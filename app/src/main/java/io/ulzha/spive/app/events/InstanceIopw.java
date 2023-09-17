package io.ulzha.spive.app.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Captures number of events input/output per window in application event time, window sizes
 * ranging from minute and up.
 *
 * <p>These event counts, exactly aggregated, serve for event volume visualization in UI.
 *
 * <p>An InstanceIopw event is spawned to describe every instance on a regular basis, meaning
 * approximately soon after end of every event time window. Finest window sizes are not spawned
 * continuously during replay. Depending on progress tracking capacity and other considerations,
 * gaps can occur at other times as well, more likely in the finer window sizes. Gaps are then
 * reflected as blur in the visualization (unless exact counts are provided from other channels).
 *
 * <p>(Will benefit from compaction - old data to be aggregated into coarser windows.)
 */
public record InstanceIopw(
    UUID instanceId, // partition key
    // these will probably need to count per stream. Expect a composite key...
    Instant windowStart,
    Instant windowEnd,
    long nInputEvents,
    long nOutputEvents
    // unsure about events emitted from workloads spontaneously, they probably aren't "output";
    // their observed count can differ from one replica to another as they race
    ) {}
