// Generated by io.ulzha.spive.codegen.GenerateIocCode - do not edit! Put application logic in Copy
// class that implements CopyInstance interface.
package io.ulzha.spive.example.copy.app.spive.gen;

import io.ulzha.spive.example.copy.app.events.CreateFoo;
import io.ulzha.spive.lib.EventGateway;
import io.ulzha.spive.lib.EventIterator;
import io.ulzha.spive.lib.EventLock;
import io.ulzha.spive.lib.EventSerde;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Glue code generated by Spīve, which facilitates strongly typed output.
 *
 * <p>The methods are merely adapting app events, via serde for the given Type, to generic
 * EventGateway interface.
 */
// <PojoAsJson, or some scheme revolving around Types>
public class CopyOutputGateway extends EventGateway {

  public CopyOutputGateway(
      UmbilicalWriter umbilicus,
      EventIterator eventIterator,
      Supplier<Instant> wallClockTime,
      EventLock eventLock) {
    super(umbilicus, eventIterator, wallClockTime, eventLock);
  }

  private static final EventSerde createFooSerde =
      EventSerde.forTypeTag("pojo:io.ulzha.spive.example.copy.app.events.CreateFoo");

  public boolean emitIf(Supplier<Boolean> check, CreateFoo payload) {
    return emitIf(check, createFooSerde, payload);
  }

  public boolean emitIf(Supplier<Boolean> check, CreateFoo payload, EventTime eventTime) {
    return emitIf(check, createFooSerde, payload, eventTime);
  }

  public void emitConsequential(CreateFoo payload) {
    emitConsequential(createFooSerde, payload);
  }
}
