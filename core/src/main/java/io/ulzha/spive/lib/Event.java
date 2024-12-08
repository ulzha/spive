package io.ulzha.spive.lib;

import java.util.UUID;

/**
 * An action or occurrence recognized by application.
 *
 * <p>Can be the occurrence of a valid command (state change that was initiated by someone invoking
 * an RPC to the application, essentially a push) or sometimes a passive observation by a periodic
 * workload (akin to polling from a cron job, or a pull).
 *
 * <p>Note that while in domain-driven design "commands" and "events" are distinct concepts, Spīve
 * idiomatically turns commands into events rather early, and therefore commands lack first class
 * representation in the architecture altogether (to the benefit of simplicity). A command becomes
 * an event by means of an application instance validating the RPC against its in-memory state and
 * atomically appending it to its input EventLog; if the append succeeds the application is
 * committed to carry out all the corresponding state changes and side effects of the command.
 *
 * <p>In case the aforementioned validation requires distributed or heavy computation, your
 * in-memory model may need to be extended to represent tentative or "draft" state changes. (A
 * typical example might be a payment form submission, which does not result in a purchase until
 * validated by the bank.) See also [Command Query Responsibility Segregation
 * (CQRS)](https://martinfowler.com/bliki/CQRS.html) - in Spīve case, it could mean a separate
 * microapplication accepting requests and later emitting commitment/rejection events, which would
 * then be consumed by another application that carries out state changes (Query Model changes, in
 * CQRS terms) and side effects.
 */
// TODO clearer... The distinction between query and command may be less important than distinction
// between momentous and sparse "work chains" (synchronous and asynchronous handlers doesn't seem
// like the same impact of a concept... Sequential wanted, in some way, but at the same time guarded
// against wasteful idling)
public class Event {
  public EventTime time;

  // event time identifies an event uniquely per log id only, so maybe we need this for universal
  // identification...?
  public UUID id;

  public EventSerde serde;
  // (A tuple of POXO class and serde id+version that wrote it)
  // (Capture also instance that wrote it? In case a zombie runner needs to be hunted down.)
  // (And serde id+version that read it? This would be constant for the duration of a process?)
  public Object payload; // the event object passed to application, perhaps class from
  // io.ulzha.spive.app.events... or not relative to io.ulzha.spive at all... should it repeat
  // the `time` field always?

  public Event(EventTime time, UUID id, EventSerde serde, Object payload) {
    this.time = time;
    this.id = id;
    this.serde = serde;
    this.payload = payload;
  }

  public Event(EventSerde serde) {
    this.serde = serde;
  }
}
