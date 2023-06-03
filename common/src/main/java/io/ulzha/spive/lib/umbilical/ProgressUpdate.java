package io.ulzha.spive.lib.umbilical;

import jakarta.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

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
    return new ProgressUpdate(Instant.now(), false, getStackTraceAsString(warning), null);
  }

  public static ProgressUpdate createError(final Throwable error) {
    return new ProgressUpdate(Instant.now(), false, null, getStackTraceAsString(error));
  }

  private static String getStackTraceAsString(final Throwable throwable) {
    final StringWriter sw = new StringWriter();
    final PrintWriter pw = new PrintWriter(sw);
    throwable.printStackTrace(pw);
    return sw.toString();
  }
}
