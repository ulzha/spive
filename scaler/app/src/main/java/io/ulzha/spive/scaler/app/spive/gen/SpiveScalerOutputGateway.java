// Generated by io.ulzha.spive.codegen.GenerateIocCode - do not edit! Put application logic in
// SpiveScaler class that implements SpiveScalerInstance interface.
package io.ulzha.spive.scaler.app.spive.gen;

import io.ulzha.spive.app.events.CreateInstance;
import io.ulzha.spive.lib.EventGateway;
import io.ulzha.spive.lib.EventIterator;
import io.ulzha.spive.lib.EventLock;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.Type;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import io.ulzha.spive.scaler.app.events.ScaleProcess;
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
// <PojoAsJson, or some scheme revolving around Types>
public class SpiveScalerOutputGateway extends EventGateway {

  public SpiveScalerOutputGateway(
      UmbilicalWriter umbilicus,
      EventIterator eventIterator,
      Supplier<Instant> wallClockTime,
      EventLock eventLock) {
    super(umbilicus, eventIterator, wallClockTime, eventLock);
  }

  private static final Type createInstanceType =
      Type.fromTypeTag("pojo:io.ulzha.spive.app.events.CreateInstance");

  private static final Type scaleProcessType =
      Type.fromTypeTag("pojo:io.ulzha.spive.scaler.app.events.ScaleProcess");

  public boolean emitIf(Supplier<Boolean> check, CreateInstance payload) {
    return emitIf(check, createInstanceType, payload);
  }

  public boolean emitIf(Supplier<Boolean> check, CreateInstance payload, EventTime eventTime) {
    return emitIf(check, createInstanceType, payload, eventTime);
  }

  public boolean emitIf(Supplier<Boolean> check, ScaleProcess payload) {
    return emitIf(check, scaleProcessType, payload);
  }

  public boolean emitIf(Supplier<Boolean> check, ScaleProcess payload, EventTime eventTime) {
    return emitIf(check, scaleProcessType, payload, eventTime);
  }

  public void emitConsequential(CreateInstance payload) {
    emitConsequential(createInstanceType, payload);
  }

  public void emitConsequential(ScaleProcess payload) {
    emitConsequential(scaleProcessType, payload);
  }
}
