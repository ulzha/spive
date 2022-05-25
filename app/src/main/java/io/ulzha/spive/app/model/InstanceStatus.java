package io.ulzha.spive.app.model;

// very minimal intelligence for now
public enum InstanceStatus {
  NOMINAL,

  // TODO special state for series of warnings?
  // Or that is maybe merely a UI on-demand thing, and certainly a duty of external monitoring.

  // All partitions on the instance are blocked waiting on one event to get handled, which has
  // already taken longer than the allotted handlerTimeout.
  // This can recover to NOMINAL if the handler eventually succeeds. Nevertheless Spive will spawn
  // a new instance, ready to replace this one if the slowness goes away in the newly spawned one.
  TIMEOUT, // (WARNING?)
  // This currently would only detect handler timeout if heartbeat was detected on handler start.
  // TODO track log sizes and issue TIMEOUT if an instance takes too long to even start handling.

  // TODO special state for series of timeouts?
  // Don't want flood of events from flapping TIMEOUT<->NOMINAL - it wouldn't be coarse anymore.

  // Event handlers or workloads threw exceptions on the instance but the rest of partitions resp.
  // workloads may still be proceeding (subject to bookkeeping limits).
  // Will never recover to NOMINAL. Spive will spawn a new instance and replace this one if the
  // errors go away in the newly spawned one.
  ERROR,

  // The end of an input stream was reached, or a workload exited normally.
  // Spive will shut the instance (and eventually the process) down.
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
