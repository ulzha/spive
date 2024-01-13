package io.ulzha.spive.app.model;

/**
 * Side effect handler/interceptor - enables live and dry runs, in particular dry runs for testing.
 *
 * <p>A Gateway encapsulates the behavior switching from replay (dry) mode to live mode as
 * necessary, an instrumental aspect of event-sourced applications.
 *
 * <p>A Gateway also may attempt to elide unnecessary copies of side effects when they occur as a
 * result of multiple redundant live Process Instances running. (This doesn't guarantee exactly-once
 * production of the side effect though; effectively-once guarantee is possible if the receiving end
 * of the side effect is idempotent.)
 *
 * <p>Production of a side effect can fail for external reasons (e.g. a 3rd party service being
 * down); retrying with a suitable backoff is another part of Gateway responsibility, something that
 * allows Process code to be kept simpler. To ensure retry, a Gateway call is expected to loop
 * forever (unless interrupted) making every effort to complete, which includes reestablishing lost
 * connections, forcibly shutting down and recreating hung clients, etc.
 *
 * <p>Moreover, a Gateway may help delineate ownership - exceptions occurring in a Gateway could
 * likely be narrowed down to the team who own the remote system that it is talking to.
 *
 * <p>Backpressure should be exhibited by Gateways so that throttling decisions can be made by Spīve
 * automatically.
 *
 * <p>Timeouts of external calls should be coordinated to not exceed (a fraction of?) event handler
 * timeout, as imposed by Spīve. (More complex when the number of external calls varies...) TODO
 * warn when exceeded (measure the interval between addWarning)
 */
public class Gateway {
  //  public EventTime lastCommittedTime;
  //  public boolean replay;

  // onEmitEvent(...) vs
  // emitEvent(...)

  // onCreateProcess(...) vs
  // createProcess(...)
}
