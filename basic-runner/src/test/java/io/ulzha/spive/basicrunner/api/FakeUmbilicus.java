package io.ulzha.spive.basicrunner.api;

import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import java.util.ArrayList;
import java.util.List;

public class FakeUmbilicus implements UmbilicalWriter {
  public List<Throwable> warnings = new ArrayList<>();
  public List<Throwable> errors = new ArrayList<>();

  @Override
  public boolean getReplayMode() {
    return false;
  }

  @Override
  public void addError(final Throwable error) {
    errors.add(error);
  }

  @Override
  public void addWarning(final Throwable warning) {
    warnings.add(warning);
  }
}
