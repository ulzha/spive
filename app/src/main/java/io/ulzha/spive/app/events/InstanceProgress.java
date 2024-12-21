package io.ulzha.spive.app.events;

import io.ulzha.spive.lib.EventTime;
import java.util.UUID;

/**
 * Captures offsets from the event loop, with reasonably coarse granularity.
 *
 * <p>The latest checkpoint persisted is used to make a replay schedule (prevent duplicate side
 * effects up to and including checkpoint) when the instance needs to be sharded or repaired.
 *
 * <p>An InstanceProgress event (unlike InstanceStatusChange) is spawned to describe every instance
 * on a regular basis, meaning approximately regularly in wall clock time.
 *
 * <p>(Will benefit from compaction - old data to be discarded.)
 */
public record InstanceProgress(
    UUID instanceId, // partition key
    EventTime checkpoint,
    // (eventually reformulate in terms of watermark?)

    // Successfully handled input events since start of instance's lifetime until checkpoint,
    // inclusive.
    long nInputEventsHandled,

    // Output events emitted since start of instance's lifetime. Some can account for events _after_
    // checkpoint, freshly emitted from spontaneous workloads or from the subsequent event's ongoing
    // handling. Output-only applications (not event-sourced) would always see checkpoint at
    // INFINITE_PAST and just nOutputEvents growing.
    long nOutputEvents

    // TODO byte[] sparkline...
    // Compact individual timings, histograms perhaps...
    // Or separate channels for that, for SpiveScaler to observe
    // Or completely unmanaged channels, logging and throughput fed back via SpiveApmConnector
    // long nWorkloadErrorsPreviousWallClockTimeWindow
    ) {}
