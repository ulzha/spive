package io.ulzha.spive.threadrunner.api;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSample;
import jakarta.json.bind.annotation.JsonbNillable;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import java.util.List;
import javax.annotation.Nullable;

@JsonbNillable(true)
@JsonbPropertyOrder({"heartbeat", "checkpoint"})
public record GetThreadGroupHeartbeatResponse(
    List<HeartbeatSample> heartbeat, @Nullable EventTime checkpoint) {
  // Slim this down again, just make sure to include the last successful event in heartbeat?

  public static GetThreadGroupHeartbeatResponse createVerbose(final Umbilical umbilical) {
    final var heartbeatSnapshot = umbilical.getHeartbeatSnapshot();
    return new GetThreadGroupHeartbeatResponse(
        heartbeatSnapshot, Umbilical.getLastHandledEventTime(heartbeatSnapshot));
  }

  public static GetThreadGroupHeartbeatResponse create(final Umbilical umbilical) {
    final var heartbeatSnapshot = umbilical.getHeartbeatSnapshot();
    return new GetThreadGroupHeartbeatResponse(
        Umbilical.getFirsts(heartbeatSnapshot),
        Umbilical.getLastHandledEventTime(heartbeatSnapshot));
  }
}
