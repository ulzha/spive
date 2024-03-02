package io.ulzha.spive.scaler.app.lib;

import io.ulzha.spive.lib.EventGateway;
import io.ulzha.spive.lib.EventIterator;
import io.ulzha.spive.lib.LockableEventLog;
import io.ulzha.spive.lib.Type;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Glue code generated by Spīve, which facilitates strongly typed output.
 *
 * <p>Thread-safe, made for use by concurrent workloads and event handlers.
 *
 * <p>The methods are merely adapting app events, via serde for the given Type, to generic
 * EventGateway interface.
 *
 * <p>This application consumes its own output stream, therefore implementations of emit* methods
 * must block on event handlers - hence the use of EventIterator to coordinate with EventLoop.
 */
public class SpiveScalerOutputGateway extends EventGateway {

  public SpiveScalerOutputGateway(
      UmbilicalWriter umbilicus,
      EventIterator eventIterator,
      Supplier<Instant> wallClockTime,
      LockableEventLog eventLog) {
    super(umbilicus, eventIterator, wallClockTime, eventLog);
  }

  private static final Type scaleProcessType =
      Type.fromTypeTag("pojo:io.ulzha.spive.scaler.app.events.ScaleProcess");

  // public void emitConsequential(ScaleProcess payload) {
  //   emitConsequential(scaleProcessType, payload);
  // }
}
