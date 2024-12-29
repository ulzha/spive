package io.ulzha.spive.app.workloads.watchdog;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;

public class FakeUmbilicus implements UmbilicalWriter {
  @Override
  public void addSuccess() {}

  @Override
  public boolean getReplayMode() {
    return false;
  }

  @Override
  public void addError(final Throwable error) {}

  @Override
  public void addWarning(final Throwable warning) {}

  @Override
  public void addHeartbeat() {}

  @Override
  public void addOutputEvent(EventTime outputEventTime) {}
}
