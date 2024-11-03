package io.ulzha.spive.app.workloads.watchdog;

import static io.ulzha.spive.app.model.InstanceStatus.ERROR;
import static io.ulzha.spive.app.model.InstanceStatus.EXIT;

import io.ulzha.spive.app.events.InstanceIopw;
import io.ulzha.spive.app.events.InstanceProgress;
import io.ulzha.spive.app.events.InstanceStatusChange;
import io.ulzha.spive.app.model.InstanceStatus;
import io.ulzha.spive.app.model.Process;
import io.ulzha.spive.app.spive.gen.SpiveOutputGateway;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.InternalException;
import io.ulzha.spive.lib.umbilical.HeartbeatSnapshot;
import io.ulzha.spive.lib.umbilical.UmbilicalReader;
import java.time.Instant;
import java.util.function.Supplier;

class PollLoop {
  private final Process.Instance instance;

  // May have some ephemeral state, accumulating fine-grained communication polled from runner.
  private final UmbilicalReader placenta;
  private final StatusTracker statusTracker;
  // sparklineTracker - visualize streaks of timed-out events even when they don't toggle status
  // discardTracker

  private final SpiveOutputGateway output;

  PollLoop(
      final Process.Instance instance,
      final UmbilicalReader placenta,
      final Supplier<Instant> wallClockTime,
      final SpiveOutputGateway output) {
    this.instance = instance;
    this.placenta = placenta;
    this.statusTracker = new StatusTracker(placenta, wallClockTime, instance.id);
    this.output = output;
  }

  /**
   * Summarize fine-grained runner observations into coarsely-grained InstanceStatus changes that
   * get persisted.
   *
   * <p>The event time offsets from heartbeat also end up as events in control plane, persisted in
   * bulk at a best effort rate (TODO the persisting rate could be adaptive, less than poll rate)
   * and compactable.
   *
   * <p>Regardless of synchronization between event handlers and emitIf, there can be another append
   * by a racing replica instance, so it is possible that emitIf exits with false. Ignoring it is
   * fine here, we don't need redundant InstanceProgress or InstanceStatusChange.
   */
  public void pollOnce() throws InterruptedException {
    if (instance.id == null) throw new InternalException("Precondition failed");
    if (instance.timeoutMillis <= 0) throw new InternalException("Precondition failed");
    if (instance.checkpoint == null) throw new InternalException("Precondition failed");
    if (instance.status == null) throw new InternalException("Precondition failed");

    HeartbeatSnapshot snapshot = placenta.updateHeartbeat();

    final EventTime checkpoint = snapshot.checkpoint();
    System.out.println("Instance " + instance.id + " checkpoint: " + checkpoint);
    output.emitIf(
        () ->
            instance.process != null
                && checkpoint != null
                && instance.checkpoint != null
                && instance.checkpoint.compareTo(checkpoint) < 0,
        new InstanceProgress(
            instance.id, checkpoint, snapshot.nInputEventsTotal(), snapshot.nOutputEventsTotal()));

    final InstanceStatusChange instanceStatusChange =
        statusTracker.getStatus(instance.timeoutMillis);
    output.emitIf(
        () ->
            instance.process != null
                && instance.status != ERROR
                && instance.status != EXIT
                && instance.status != InstanceStatus.valueOf(instanceStatusChange.status()),
        instanceStatusChange);
    // With TIMEOUT sometimes the cause will appear as a warning in a later update. TODO capture
    // as a new event? calculate the time contribution to prevent red herrings? identify the cause
    // with profiling even if no gateway explicitly retried a call? trueCauseTracker? on demand
    // only, via frontend?

    // TODO skimp on this and prioritize just progress and status change when in distress
    for (var iopw : placenta.updateIopws(selectTimelineIopwToPoll(instance))) {
      output.emitIf(
          () -> instance.process != null,
          new InstanceIopw(
              instance.id,
              iopw.windowStart(),
              iopw.windowEnd(),
              iopw.nInputEvents(),
              iopw.nOutputEvents()));
    }
  }

  /**
   * Avoids refetching timeline iopws; also decides when to skip forward instead of a backfill of
   * undue length.
   *
   * <p>TODO takes into account where errors and warnings are seen in heartbeat.
   */
  private Instant selectTimelineIopwToPoll(final Process.Instance instance) {
    final Process process = instance.process;
    if (process != null) {
      final Instant currEnd = instance.timeline.getMinuteEnd();
      if (currEnd != null) {
        return currEnd;
      }
      if (process.startTime != null) {
        return process.startTime.instant;
      }
    }
    return null;
  }
}
