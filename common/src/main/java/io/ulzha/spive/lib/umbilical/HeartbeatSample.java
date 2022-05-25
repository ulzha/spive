package io.ulzha.spive.lib.umbilical;

import com.google.auto.value.AutoValue;
import io.ulzha.spive.lib.EventTime;
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
public abstract class HeartbeatSample {
  @Nullable
  public abstract EventTime eventTime();

  @Nullable
  public abstract String partition();

  public abstract List<ProgressUpdate> progressUpdates();

  public static HeartbeatSample create(
      final EventTime eventTime,
      final String partition,
      final List<ProgressUpdate> progressUpdates) {
    return new AutoValue_HeartbeatSample(eventTime, partition, progressUpdates);
  }
}
