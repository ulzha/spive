package io.ulzha.spive.lib.umbilical;

import com.google.common.base.Throwables;
import java.time.Instant;
import javax.annotation.Nullable;

/** TODO neaten up as SuccessUpdate, WarningUpdate, ErrorUpdate? */
public record ProgressUpdate(
    Instant instant, boolean success, @Nullable String warning, @Nullable String error) {
  public ProgressUpdate() {
    this(Instant.now(), false, null, null);
  }

  public static ProgressUpdate createSuccess() {
    return new ProgressUpdate(Instant.now(), true, null, null);
  }

  public static ProgressUpdate createWarning(final Throwable warning) {
    return new ProgressUpdate(
        Instant.now(), false, Throwables.getStackTraceAsString(warning), null);
  }

  public static ProgressUpdate createError(final Throwable error) {
    return new ProgressUpdate(Instant.now(), false, null, Throwables.getStackTraceAsString(error));
  }
}
