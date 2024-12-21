package io.ulzha.spive.lib.umbilical;

import io.ulzha.spive.lib.EventTime;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * The minimal "beat" to communicate from data plane to control plane.
 *
 * <p>Not broken up onto separate channels because of desire for point-in-time consistency.
 */
// CompressedHeartbeat?
public record HeartbeatSnapshot(
    List<ProgressUpdatesList> sample,
    @Nullable EventTime checkpoint,
    long nInputEventsHandled,
    long nOutputEvents) {}
