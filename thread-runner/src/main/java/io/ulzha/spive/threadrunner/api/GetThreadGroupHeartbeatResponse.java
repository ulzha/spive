package io.ulzha.spive.threadrunner.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSample;
import java.util.List;
import javax.annotation.Nullable;

@AutoValue
public abstract class GetThreadGroupHeartbeatResponse {
  @JsonProperty("heartbeat")
  public abstract List<HeartbeatSample> heartbeat();

  @Nullable
  public abstract EventTime checkpoint();

  // Here we need this but for map keys Jackson seems to automatically use toString and fromString ^
  @JsonGetter("checkpoint")
  public String getCheckpoint() {
    return (checkpoint() == null ? null : checkpoint().toString());
  }
  // Slim this down again, just make sure to include the last successful event in heartbeat?

  @JsonCreator
  public static GetThreadGroupHeartbeatResponse create(
      @JsonProperty("heartbeat") final List<HeartbeatSample> heartbeat,
      @JsonProperty("checkpoint") final EventTime checkpoint) {
    return new AutoValue_GetThreadGroupHeartbeatResponse(heartbeat, checkpoint);
  }

  public static GetThreadGroupHeartbeatResponse createVerbose(final Umbilical umbilical) {
    final var heartbeatSnapshot = umbilical.getHeartbeatSnapshot();
    return create(heartbeatSnapshot, Umbilical.getLastHandledEventTime(heartbeatSnapshot));
  }

  public static GetThreadGroupHeartbeatResponse create(final Umbilical umbilical) {
    final var heartbeatSnapshot = umbilical.getHeartbeatSnapshot();
    return create(
        Umbilical.getFirsts(heartbeatSnapshot),
        Umbilical.getLastHandledEventTime(heartbeatSnapshot));
  }
}
