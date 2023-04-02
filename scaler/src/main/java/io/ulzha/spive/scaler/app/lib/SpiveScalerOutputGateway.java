package io.ulzha.spive.scaler.app.lib;

import io.ulzha.spive.lib.Event;
import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.Gateway;
import io.ulzha.spive.lib.LockableEventLog;
import io.ulzha.spive.lib.Type;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import java.io.IOException;
import java.time.Instant;
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
public class SpiveScalerOutputGateway extends Gateway {
  private final AtomicReference<EventTime> lastEventTime;
  private final AtomicReference<EventTime> lastEventTimeEmitted;
  private final Supplier<Instant> wallClockTime;
  private final LockableEventLog eventLog;

  private static final Type createInstanceType =
      Type.fromTypeTag("pojo:io.ulzha.spive.app.events.CreateInstance");

  public SpiveScalerOutputGateway(
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

  public void emitConsequential() {
    // TODO

    eventLog.lockConsequential();
    System.out.println(
        this.lastEventTime.toString() + lastEventTimeEmitted.toString() + wallClockTime.toString());
    emit(null, EventTime.INFINITE_PAST);
  }

  /**
   * Blocks indefinitely until append occurs or a competing prior append has been positively
   * detected.
   *
   * @return true if appended, false if not because the latest stored Event has time >
   *     lastEventTime.
   * @throws IllegalArgumentException if event.time <= lastEventTime.
   */
  private boolean emit(Event event, EventTime lastEventTime) {
    // TODO check that it belongs to the intended stream and the intended subset of partitions
    long sleepMs = 10;
    long sleepMsMax = 100000;
    EventEnvelope ee = EventEnvelope.wrap(event);
    while (true) {
      try {
        return eventLog.appendIfPrevTimeMatch(ee, lastEventTime);
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
}
