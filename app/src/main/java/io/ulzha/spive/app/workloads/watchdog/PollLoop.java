package io.ulzha.spive.app.workloads.watchdog;

import static io.ulzha.spive.app.model.InstanceStatus.ERROR;
import static io.ulzha.spive.app.model.InstanceStatus.EXIT;

import io.ulzha.spive.app.events.InstanceProgress;
import io.ulzha.spive.app.events.InstanceStatusChange;
import io.ulzha.spive.app.lib.SpiveOutputGateway;
import io.ulzha.spive.app.model.InstanceStatus;
import io.ulzha.spive.app.model.Process;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.UmbilicalReader;
import java.time.Instant;
import java.util.function.Supplier;

class PollLoop {
  private final Process.Instance instance;

  // May have some ephemeral state, accumulating fine-grained communication polled from runner.
  private final UmbilicalReader placenta;
  private final ProgressTracker progressTracker;
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
    this.progressTracker = new ProgressTracker(placenta);
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
    assert (instance.id != null);
    assert (instance.timeoutMillis > 0);
    assert (instance.checkpoint != null);
    assert (instance.status != null);

    placenta.updateHeartbeat();

    final EventTime checkpoint = progressTracker.getCheckpoint();
    output.emitIf(
        () ->
            instance.process != null
                && checkpoint != null
                && instance.checkpoint != null
                && instance.checkpoint.compareTo(checkpoint) < 0,
        new InstanceProgress(instance.id, checkpoint));

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
  }
}
