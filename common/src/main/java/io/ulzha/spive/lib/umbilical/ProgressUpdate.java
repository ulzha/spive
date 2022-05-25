package io.ulzha.spive.lib.umbilical;

import com.google.auto.value.AutoValue;
import com.google.common.base.Throwables;
import java.time.Instant;
import javax.annotation.Nullable;

/** TODO neaten up as SuccessUpdate, WarningUpdate, ErrorUpdate? */
@AutoValue
public abstract class ProgressUpdate {
  public abstract Instant instant();

  public abstract boolean success();

  @Nullable
  public abstract String warning();

  @Nullable
  public abstract String error();

  public static ProgressUpdate create(
      final Instant instant, final boolean success, final String warning, final String error) {
    return new AutoValue_ProgressUpdate(instant, success, warning, error);
  }

  public static ProgressUpdate create() {
    return new AutoValue_ProgressUpdate(Instant.now(), false, null, null);
  }

  public static ProgressUpdate createSuccess() {
    return create(Instant.now(), true, null, null);
  }

  public static ProgressUpdate createWarning(final Throwable warning) {
    return create(Instant.now(), false, Throwables.getStackTraceAsString(warning), null);
  }

  public static ProgressUpdate createError(final Throwable error) {
    return create(Instant.now(), false, null, Throwables.getStackTraceAsString(error));
  }
}
