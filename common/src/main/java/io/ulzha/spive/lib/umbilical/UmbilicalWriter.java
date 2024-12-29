package io.ulzha.spive.lib.umbilical;

import io.ulzha.spive.lib.EventTime;

/** (Initially aimed to just use some functional interfaces but they became many) */
// It's confusing to talk direction but less confusing to talk scope?
// InstanceStartReporter & InstanceExitReporter
// = InstanceUmbilical?
// HandlingStartReporter & HandlingErrorReporter & WriteReporter
// = EventLoopUmbilical?
// HandlingErrorPreReporter & WriteReporter
// = OutputUmbilical?
// WarningReporter & HandlingErrorPreReporter & replayModeSupplier
// = GatewayUmbilical?
public interface UmbilicalWriter {
  public boolean getReplayMode();

  public void addSuccess();

  /**
   * Added by instance, whenever an event handler crashes (permanent failure). Also added by gateway
   * before exiting to the handler with a permanent failure.
   *
   * @param error
   */
  public void addError(Throwable error);

  /**
   * Added by gateway before retrying on intermittent failures.
   *
   * @param warning
   */
  public void addWarning(Throwable warning);

  /**
   * Set by event loop before every event handled.
   *
   * @param warning
   */
  public void addHeartbeat();

  /**
   * Called by output gateway after every event emitted, be it from event loop or from a concurrent
   * workload.
   *
   * <p>NB: only the leading (true) emission of the event is counted, trailing (idempotent)
   * emissions are not. Idempotent emissions would occur as a result of reprocessing preexisting
   * events with side effects, as well as with new events, in active-active configuration, when
   * replicas emit concurrently. It holds true that the sum of counts across all replicas equals the
   * total number of new events _added_ to output stream(s) by the process as a whole.
   */
  public void addOutputEvent(EventTime outputEventTime);
}
