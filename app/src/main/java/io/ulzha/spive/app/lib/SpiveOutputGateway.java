package io.ulzha.spive.app.lib;

import io.ulzha.spive.app.events.CreateInstance;
import io.ulzha.spive.app.events.CreateProcess;
import io.ulzha.spive.app.events.CreateType;
import io.ulzha.spive.app.events.DeleteInstance;
import io.ulzha.spive.app.events.InstanceIopw;
import io.ulzha.spive.app.events.InstanceProgress;
import io.ulzha.spive.app.events.InstanceStatusChange;
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
// <PojoAsJson, or some scheme revolving around Types>
public class SpiveOutputGateway extends EventGateway {

  public SpiveOutputGateway(
      UmbilicalWriter umbilicus,
      EventIterator eventIterator,
      Supplier<Instant> wallClockTime,
      LockableEventLog eventLog) {
    super(umbilicus, eventIterator, wallClockTime, eventLog);
  }

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

  public boolean emitIf(Supplier<Boolean> check, InstanceStatusChange payload) {
    return emitIf(check, instanceStatusChangeType, payload);
  }

  public void emitConsequential(CreateInstance payload) {
    emitConsequential(createInstanceType, payload);
  }

  public void emitConsequential(DeleteInstance payload) {
    emitConsequential(deleteInstanceType, payload);
  }
}
