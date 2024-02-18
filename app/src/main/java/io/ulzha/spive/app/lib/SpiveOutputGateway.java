package io.ulzha.spive.app.lib;

import io.ulzha.spive.app.events.CreateInstance;
import io.ulzha.spive.app.events.CreateProcess;
import io.ulzha.spive.app.events.CreateType;
import io.ulzha.spive.app.events.DeleteInstance;
import io.ulzha.spive.app.events.InstanceIopw;
import io.ulzha.spive.app.events.InstanceProgress;
import io.ulzha.spive.app.events.InstanceStatusChange;
import io.ulzha.spive.lib.Event;
import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.Gateway;
import io.ulzha.spive.lib.LockableEventLog;
import io.ulzha.spive.lib.Type;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Glue code generated by Spīve, which facilitates strongly typed output.
 *
 * <p>TODO make thread-safe, as this is for use by concurrent workloads
 *
 * <p>The methods are merely adapting app events, via serde for the given Type, to EventLog
 * interface, while implementing the Gateway contract.
 */
public class SpiveOutputGateway /*<PojoAsJson, or some scheme revolving around Types> */
    extends Gateway {
  private final AtomicReference<EventTime> lastEventTime;
  private final AtomicReference<EventTime> lastEventTimeEmitted;
  private final Supplier<Instant> wallClockTime;
  private final LockableEventLog eventLog;

  // (protobuf Any, anyone? type_url: "type.googleapis.com/company.entity.Foo")
  private static final Type createTypeType =
      Type.fromTypeTag("pojo:io.ulzha.spive.app.events.CreateType");
  private static final Type createInstanceType =
      Type.fromTypeTag("pojo:io.ulzha.spive.app.events.CreateInstance");
  private static final Type createProcessType =
      Type.fromTypeTag("pojo:io.ulzha.spive.app.events.CreateProcess");
  private static final Type createStreamType =
      Type.fromTypeTag("pojo:io.ulzha.spive.app.events.CreateStream");
  private static final Type deleteInstanceType =
      Type.fromTypeTag("pojo:io.ulzha.spive.app.events.DeleteInstance");
  private static final Type deleteProcessType =
      Type.fromTypeTag("pojo:io.ulzha.spive.app.events.DeleteProcess");
  private static final Type instanceIopwType =
      Type.fromTypeTag("pojo:io.ulzha.spive.app.events.InstanceIopw");
  private static final Type instanceProgressType =
      Type.fromTypeTag("pojo:io.ulzha.spive.app.events.InstanceProgress");
  private static final Type instanceStatusChangeType =
      Type.fromTypeTag("pojo:io.ulzha.spive.app.events.InstanceStatusChange");

  public SpiveOutputGateway(
      final UmbilicalWriter umbilicus,
      final AtomicReference<EventTime> lastEventTime,
      final Supplier<Instant> wallClockTime,
      final LockableEventLog eventLog) {
    super(umbilicus);
    this.lastEventTime = lastEventTime;
    this.lastEventTimeEmitted = new AtomicReference<>(EventTime.INFINITE_PAST);
    this.wallClockTime = wallClockTime;
    this.eventLog = eventLog;
  }

  /**
   * Blocks indefinitely until append occurs or a competing prior append has been positively
   * detected.
   *
   * @return true if appended, false if not because the latest stored Event has time > prevTime.
   * @throws IllegalArgumentException if event.time <= prevTime.
   */
  private boolean emit(Event event, EventTime prevTime) {
    if (event.time.compareTo(prevTime) <= 0) {
      throw new IllegalArgumentException("event time must come strictly after prevTime");
    }
    // TODO check that it belongs to the intended stream and the intended subset of partitions
    long sleepMs = 10;
    long sleepMsMax = 100000;
    EventEnvelope ee = EventEnvelope.wrap(event);
    while (true) {
      try {
        return eventLog.appendIfPrevTimeMatch(ee, prevTime);
        // TODO report that we're leading
      } catch (IOException e) {
        // likely an intermittent failure, let's keep trying
        umbilicus.addWarning(e);
      }
      // unlisted exceptions are likely permanent failures, let them crash the instance
      try {
        sleepMs = Math.min(sleepMs * 10, sleepMsMax);
        Thread.sleep(sleepMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
  }

  public boolean emitIf(Supplier<Boolean> check, CreateProcess payload) {
    return emitIf(check, createProcessType, payload);
  }

  public boolean emitIf(Supplier<Boolean> check, CreateType payload) {
    return emitIf(check, createTypeType, payload);
  }

  public boolean emitIf(Supplier<Boolean> check, DeleteInstance payload) {
    return emitIf(check, deleteInstanceType, payload);
  }

  public boolean emitIf(Supplier<Boolean> check, InstanceIopw payload) {
    return emitIf(check, instanceIopwType, payload);
  }

  public boolean emitIf(Supplier<Boolean> check, InstanceProgress payload) {
    return emitIf(check, instanceProgressType, payload);
  }

  /**
   * Emits between event handlers, first checking if the check returns true for in-memory state at
   * that point in time.
   *
   * <p>Blocks until either an append occurs, which may be indefinitely preempted by competing
   * appends, or until the check returns false.
   *
   * <p>TODO non-blocking version to tolerate setbacks and give users immediate UI responses instead
   * of timeouts... An event handler can be stuck for very long, and then so would be the request
   * handler using emitIf.
   */
  // `Predicate<Spive> check`? So it's clearer what state is accessible?
  public boolean emitIf(Supplier<Boolean> check, InstanceStatusChange payload) {
    return emitIf(check, instanceStatusChangeType, payload);
  }

  private <T> boolean emitIf(Supplier<Boolean> check, Type type, T payload) {
    try {
      // Await until at least the latest event written by this gateway is read back. Otherwise,
      // when a sporadic workload attempts to emit multiple events in a quick succession, the second
      // and subsequent ones would exceedingly likely get rejected, which would result in
      // unintuitive developer experience when writing sporadic workloads, particularly with
      // non-distributed applications in mind. (In distributed applications, when instances race
      // each other, rejections can still result, and there they need to be dealt with, and their
      // existence can _not_ be considered overly counterintuitive.)
      // Dunno if we'd ever grow support for sporadic workloads without event handlers? Would
      // perhaps alter this snippet then.
      // awaitWithBackoff(lastEventTime.get().compareTo(lastEventTimeEmitted.get()) >= 0);
      final EventTime eventTime = awaitAdvancing();

      try {
        eventLog.lock();
        // Unsure if replay mode is applicable to concurrent workloads at the same times when it's
        // applicable to event handlers... Roughly it might be, but whenever we have just entered
        // trailing state and not yet toggled replay on, an attempt at fulfilling side effects will
        // occur and will likely block for almost an eternity...?
        // Interrupt a concurrent workflow thread that encounters a conflict, but return normally
        // when it's an event handler?
        // Or just return false... And have a void version for use in event handlers, which are
        // supposed to assume it went through? Well nooo...
        // If a concurrent workload appends a delete, then an event handler for a prior event will
        // not validly append anything anymore. So handler code shouldn't assume an append went
        // through. A handler should anyway only append an immediate event, never anything in future
        // in event time, so (with some specialized synchronization across tiebreaker) there are
        // fewer valid scenarios? If a handler has done that then we know it locally and postpone
        // the concurrent workload's sync?
        // Later, more generally - postpone over consequential events even if they aren't immediate
        // in event time? Given nothing else writes to output than us, we know locally which events
        // have consequences coming... so it doesn't even need to be persisted in the stream anyhow.
        // Events from handlers are always consequential to some input event (deterministically
        // computed at that), unlike events from workloads.
        // There can be streaks of consequential events longer than 2.
        // When we consume a stream to which we do not output, we will not know which events are
        // consequential, but that's no problem...
        //      if (replayModeSupplier.get()) {
        //        return;
        //      }
        if (check.get()) {
          final Event event = new Event(eventTime, UUID.randomUUID(), type, payload);
          if (emit(event, lastEventTime.get())) {
            lastEventTimeEmitted.set(eventTime);
            return true;
          } // FIXME else loop
        }
        return false;
      } finally {
        eventLog.unlock();
      }
    } catch (Throwable t) {
      umbilicus.addError(t);
      throw t;
    }
  }

  /**
   * Picks the earliest event time that may be used for the next event so that an append operation
   * has chances of succeeding, according to information available in local instance.
   *
   * <p>This method blocks at least until event handling operations (locking reads) from the
   * underlying log have caught up with the last event emitted, as observed by current thread.
   *
   * <p>Note that the returned event time may nevertheless fall earlier than the latest event time
   * actually present in the underlying log, in case of a concurrent append from a sporadic workload
   * thread or a remote instance.
   *
   * @return an EventTime that closely corresponds to wall clock time and is not simultaneous with
   *     (has an instant strictly larger than the instant of) the last event time read from the
   *     underlying log.
   */
  private EventTime awaitAdvancing() {
    long waitMillis = 1;
    long waitMillisMax = 1000;

    // in testing, this Supplier call also advances lastEventTime. Should create a cleaner harness
    Instant tentativeInstant = wallClockTime.get();

    while (lastEventTime.get().compareTo(lastEventTimeEmitted.get()) < 0
        || tentativeInstant.compareTo(lastEventTime.get().instant) <= 0) {
      try {
        Thread.sleep(waitMillis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
      waitMillis = Math.min(waitMillis * 2, waitMillisMax);
      tentativeInstant = wallClockTime.get();
    }
    return new EventTime(tentativeInstant);
  }

  public void emitConsequential(CreateInstance payload) {
    emitConsequential(createInstanceType, payload);
  }

  public void emitConsequential(DeleteInstance payload) {
    emitConsequential(deleteInstanceType, payload);
  }

  /**
   * Emits an event to the output simultaneous with the event being handled.
   *
   * <p>If the output stream is also an input, then the tiebreaker in event time gets incremented.
   * Otherwise the event time is the same as for the input event currently handled. FIXME (TODO
   * non-simultaneous version?)
   *
   * <p>For use in event handlers. Ensures that workloads would not append any sporadic event in
   * between. (Haven't yet thought about situations where the consequence comes a significant time
   * after the cause... Then simultaneous times would not make much sense. Moreover the resulting
   * blocking of sporadic appends would perhaps be unacceptable.)
   *
   * <p>(The whole emission of consequential events could be considered kind of redundant, as the
   * same can be achieved by invoking event handlers from event handlers, at exit... Unsure if we
   * really need them persisted...
   *
   * <p>Perhaps nice when they act as snapshottable checkpoints between very time-consuming steps?
   * One could do that with regular events, and just require a stronger kind of determinism in event
   * handlers, such that workload events do not alter behavior...
   *
   * <p>Perhaps for most part they just form a nice-to-have convention that the sequence of
   * invocations of each handler logic is obvious from logs, instead of being nested and implicit at
   * times? Perhaps emitConsequential is just syntactic sugar for setting event time automagically
   * without developer concern?)
   */
  // emitAfter?
  // emitAlso?
  // emitTherefore?
  private <T> void emitConsequential(Type type, T payload) {
    try {
      final EventTime prevTime = lastEventTime.get();
      final EventTime eventTime = new EventTime(prevTime.instant, prevTime.tiebreaker + 1);
      try {
        eventLog.lockConsequential();
        // If emitConsequential gets erroneously invoked from another thread than EventLoop, then
        // this deadlocks immediately, which points out the problem well... Perhaps should add a
        // descriptive exception though.
        final Event event = new Event(eventTime, UUID.randomUUID(), type, payload);
        if (emit(event, prevTime)) {
          System.out.println("Emitted consequential event, wdyt? " + eventTime);
          lastEventTimeEmitted.set(eventTime);
        } else {
          // TODO only throw if something worse than a competing identical append happened...
          throw new RuntimeException("not well thought out");
        }
      } finally {
        eventLog.unlock();
      }
    } catch (Throwable t) {
      umbilicus.addError(t);
      throw t;
    }
  }
}
