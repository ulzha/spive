package io.ulzha.spive.lib;

import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

public class EventGateway extends Gateway {
  private final EventIterator eventIterator;
  private final Supplier<Instant> wallClockTime;
  private final LockableEventLog eventLog;

  public EventGateway(
      final UmbilicalWriter umbilicus,
      final EventIterator eventIterator,
      final Supplier<Instant> wallClockTime,
      final LockableEventLog eventLog) {
    super(umbilicus);
    this.eventIterator = eventIterator;
    this.wallClockTime = wallClockTime;
    this.eventLog = eventLog;
  }

  /**
   * Blocks indefinitely until an append occurs or a competing prior append has been positively
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

  /**
   * Emits between event handlers, first checking if the check returns true for in-memory state at
   * that point in time.
   *
   * <p>Blocks until either an append occurs, which may be indefinitely preempted by competing
   * appends, or until the check returns false.
   *
   * <p>TODO possible to provide fairer guarantees than indefinitely?
   *
   * <p>TODO a version with deadline? For predictable response time in server scenarios
   *
   * <p>TODO emitWhen version that evaluates check on every transition? Or is that higher level API
   */
  // `Predicate<Spive> check`? So it's clearer what state is accessible?
  protected <T> boolean emitIf(Supplier<Boolean> check, Type type, T payload) {
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
          final EventEnvelope wanted = EventEnvelope.wrap(event);
          final EventEnvelope actual = eventIterator.appendOrPeek(wanted);
          if (actual == wanted) {
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
   * Emits between event handlers, first checking if the check returns true for in-memory state at
   * eventTime.
   *
   * <p>Blocks until either successfully appended (which may be preempted by a burst of competing
   * appends, even if at call time all events in the log have time < eventTime), or until a winning
   * prior append has been positively detected (the latest event read from the log has time >=
   * eventTime), or until the check returns false.
   *
   * <p>TODO possible to provide fairer guarantees than haphazard competition?
   */
  protected <T> boolean emitIf(Supplier<Boolean> check, Type type, T payload, EventTime eventTime) {
    try {
      awaitAdvancing();

      try {
        eventLog.lock();
        if ((eventIterator.lastTimeRead == null
                || eventIterator.lastTimeRead.compareTo(eventTime) < 0)
            && (eventIterator.lastTimeEmitted == null
                || eventIterator.lastTimeEmitted.compareTo(eventTime) < 0)) {
          if (check.get()) {
            final Event event = new Event(eventTime, UUID.randomUUID(), type, payload);
            final EventEnvelope wanted = EventEnvelope.wrap(event);
            final EventEnvelope actual = eventIterator.appendOrPeek(wanted);
            if (actual == wanted) {
              return true;
            } // FIXME else loop
          }
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
   * <p>This method blocks at least until the given instance has handled the last event emitted by
   * itself. FIXME
   *
   * <p>Note that the returned event time may nevertheless fall earlier than the latest event time
   * actually present in the underlying log, in case of a concurrent append from a sporadic workload
   * thread or a remote instance.
   *
   * @return an EventTime that closely corresponds to wall clock time and is not simultaneous with
   *     (has an instant strictly larger than the instant of) the last event time read from the
   *     underlying log.
   */
  // iterator.awaitAppendable? iterator.waitLeading? iterator.catchUp? iterator.waitWhileHasNext()?
  private EventTime awaitAdvancing() {
    long waitMillis = 1;
    long waitMillisMax = 1000;

    // in testing, this Supplier call also advances eventIterator. Should create a cleaner harness
    Instant tentativeInstant = wallClockTime.get();

    while (eventIterator.lastTimeRead.compareTo(eventIterator.lastTimeEmitted) < 0
        || tentativeInstant.compareTo(eventIterator.lastTimeRead.instant) <= 0) {
      try {
        // here we can wait() on the iterator or lastEventTimeEmitted, no need to back off
        // but actually this whole function needs to be different when output isn't an input TODO
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

  /**
   * Emits an event to the output simultaneous with the event being handled.
   *
   * <p>For use in event handlers. Ensures that workloads would not append any sporadic event in
   * between.
   *
   * <p>If the output stream is also an input, then the tiebreaker in event time gets incremented.
   * Otherwise the event time is the same as for the input event currently handled. FIXME
   *
   * <p>TODO what is the well-defined order of consequences of consequences, across shards?
   *
   * <p>TODO non-simultaneous version? (Haven't yet thought much about situations where the
   * consequence comes a significant time after the cause... Then simultaneous times would not make
   * much sense. From long running handlers, long blocking of sporadic appends would result -
   * irrelevant without sporadic workloads? Would scheduling for future be one great use case? Just
   * in the shape of a higher-abstraction helper, emitConsequentialIf...At...?)
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
  // emitEffect?
  // emitTherefore?
  protected <T> void emitConsequential(Type type, T payload) {
    try {
      try {
        eventLog.lockConsequential();
        final EventTime eventTime = nextConsequentialTime();
        final Event event = new Event(eventTime, UUID.randomUUID(), type, payload);
        final EventEnvelope wanted = EventEnvelope.wrap(event);
        final EventEnvelope actual = eventIterator.appendOrPeek(wanted);
        if (actual == wanted) {
          // we actually appended
          System.out.println("Led with a consequential event, wdyt? " + eventTime);
        } else {
          // must act idempotent anyway, as we came here from an event handler
          if (!actual.equals(wanted)) {
            throw new IllegalStateException(
                "Could not emit consequential event "
                    + wanted.time()
                    + ": Unexpected event in log "
                    + actual.time()
                    + " - possible nondeterminism in handler code, or corrupt log");
          }
          System.out.println("Trailed with a consequential event, wdyt? " + eventTime);
        }
      } finally {
        eventLog.unlock();
      }
    } catch (Throwable t) {
      umbilicus.addError(t);
      throw t;
    }
  }

  /** Caters for one or multiple consequential events. */
  private EventTime nextConsequentialTime() {
    EventTime prevTime = eventIterator.lastTimeRead;
    if (eventIterator.lastTimeEmitted.compareTo(prevTime) > 0) {
      prevTime = eventIterator.lastTimeEmitted;
    }
    return new EventTime(prevTime.instant, prevTime.tiebreaker + 1);
  }
}
