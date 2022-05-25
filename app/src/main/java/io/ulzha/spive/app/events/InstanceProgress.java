package io.ulzha.spive.app.events;

import io.ulzha.spive.lib.EventTime;
import java.util.UUID;
import lombok.Data;

/**
 * Captures offsets from the event loop, with reasonably coarse granularity.
 *
 * <p>The latest checkpoint persisted is used to make a replay schedule (prevent duplicate side
 * effects up to and including checkpoint) when the instance needs to be sharded or repaired.
 *
 * <p>(Will benefit from compaction.)
 */
@Data
public class InstanceProgress {
  public UUID instanceId; // partition key
  public EventTime checkpoint;
  // TODO byte[] sparkline;

  public InstanceProgress(UUID instanceId, EventTime checkpoint) {
    this.instanceId = instanceId;
    this.checkpoint = checkpoint;
  }
}
