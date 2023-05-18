package io.ulzha.spive.app.events;

import io.ulzha.spive.lib.EventTime;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Captures instance status changes from an instance.
 *
 * <p>Used to make educated decisions to redeploy (and/or tweak the resources for) the affected
 * application, and to capture a summary of the underlying causes.
 *
 * <p>(May benefit from compaction.)
 */
public record InstanceStatusChange(
    // partition key
    UUID instanceId,
    //  String sourceWorkload, // a Workload, possibly "EventLoop"

    // the event which the instance was processing; null until the first event is read
    // also null when unknown
    @Nullable EventTime eventTime,

    // clock on the instance
    // null when unknown
    // For TIMEOUT status, this corresponds to start of event handling, plus timeout duration.
    @Nullable Instant instant,

    // model.InstanceStatus? TODO figure out UX with that - field types from application's own model
    String status,

    // stacktrace etc. Add plenty more structure later
    // For TIMEOUT status, this captures the first warning before timeout was detected. Thus it is
    // more like "suspect cause" - Spīve does not know for sure. If no warning is detected before
    // that
    // point, `null` will be captured. (UI might still have capability to look up stack traces on
    // demand, either in heartbeat before it is garbage-collected, or in an external log collection
    // system. Nevertheless, absence of `cause` can be interpreted as a signal that handlers
    // themselves are taking the time, or that deadlines in gateways are too long. It is recommended
    // to configure gateways with deadlines short enough that a few rounds of retry occur already
    // before the event handler times out, so that informative warning messages are captured in
    // `cause`.)
    // TODO capture thread dumps? Always, or when missing cause, or only on demand? Would that be a
    // runner feature perhaps, part of external debug suite?
    @Nullable String cause) {
  // EventTime previousEventTime,
  // TODO short list? Last few in the partition (that current event comes from), or last few in
  // the
  // whole partition set? Something like that could help in troubleshooting when the error is
  // nondeterministic wrt ordering across partitions or partition distribution across shards

  // TODO https://github.com/Randgalt/record-builder?
  public static class Draft {
    public UUID instanceId;
    public @Nullable EventTime eventTime;
    public @Nullable Instant instant;
    public String status;
    public @Nullable String cause;
  }
}
