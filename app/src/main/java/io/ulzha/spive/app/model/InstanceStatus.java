package io.ulzha.spive.app.model;

// very minimal intelligence for now
public enum InstanceStatus {
  // STARTUP? (STARTING? PROVISIONING?)
  NOMINAL,

  // TODO special state for series of warnings?
  // Or that is maybe merely a UI on-demand thing, and certainly a duty of external monitoring.

  // All partitions on the instance are blocked waiting on one event to get handled, which has
  // already taken longer than the allotted handlerTimeout.
  // This can recover to NOMINAL if the handler eventually succeeds. Nevertheless Spive will spawn
  // a new instance, ready to replace this one if the slowness goes away in the newly spawned one.
  STALL, // (WARNING? MANY_WARNINGS? SETBACK? DELINQUENT? TIMEOUT?)
  // This currently would only detect handler timeout if heartbeat was detected on handler start.
  // TODO track log sizes and issue STALL if an instance takes too long to even start handling.

  // TODO special state for series of stalls?
  // Don't want flood of events from flapping STALL<->NOMINAL - it wouldn't be coarse anymore.

  // HOLD? (PAUSE?)

  // Event handlers or workloads threw exceptions on the instance but the rest of partitions resp.
  // workloads may still be proceeding (subject to bookkeeping limits).
  // Will never recover to NOMINAL. Spive will spawn a new instance and replace this one if the
  // errors go away in the newly spawned one.
  ERROR, // (CRASH?)
  // But how to disown?... Many aggregations, all point-in-time operations become
  // meaningless/impossible in presence of stuck partitions.
  // How to call it when the app logic is known to be free of immediate consequential events and
  // free of aggregations?

  // The end of a closed input stream was reached, or a workload exited normally.
  // Spive will shut the instance down (and eventually the process, if all instances exit).
  EXIT,
  // Unsure also about what if a workload exits normally on one instance but keeps running on
  // other instances. Perhaps EXIT should more appropriately be "notify owner and keep state until
  // manual choice between deletion and resuscitation"? Because this mode would likely be used for
  // one-off experiments where one might want to inspect results as well as in-memory state.

  // TODO special state for a permanent failure? So Spive would skip wasteful respawning?
  // Unable to poll umbilical due to connection or overload issues.
  // LOSING (DISCONNECTED?) TODO
  // Umbilical not found even though the connection is up and the runner is healthy.
  // LOST (same as ERROR? Not sure) TODO
}
