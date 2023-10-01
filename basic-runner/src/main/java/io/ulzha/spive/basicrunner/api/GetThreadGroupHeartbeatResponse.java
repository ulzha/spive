package io.ulzha.spive.basicrunner.api;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.ProgressUpdatesList;
import jakarta.annotation.Nullable;
import jakarta.json.bind.annotation.JsonbNillable;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import java.util.List;

@JsonbNillable(true)
@JsonbPropertyOrder({"sample", "checkpoint", "nInputEventsTotal", "nOutputEventsTotal"})
public record GetThreadGroupHeartbeatResponse(
    List<ProgressUpdatesList> sample,
    @Nullable EventTime checkpoint,
    long nInputEventsTotal,
    long nOutputEventsTotal) {

  // maybe a bit silly indirection; keeping HeartbeatSnapshot class (common module as such) clean of
  // API/serde concerns
  public static GetThreadGroupHeartbeatResponse createVerbose(final Umbilical umbilical) {
    final var heartbeatSnapshot = umbilical.getHeartbeatSnapshot(true);
    return new GetThreadGroupHeartbeatResponse(
        heartbeatSnapshot.sample(),
        heartbeatSnapshot.checkpoint(),
        heartbeatSnapshot.nInputEventsTotal(),
        heartbeatSnapshot.nOutputEventsTotal());
  }

  public static GetThreadGroupHeartbeatResponse create(final Umbilical umbilical) {
    final var heartbeatSnapshot = umbilical.getHeartbeatSnapshot(false);
    return new GetThreadGroupHeartbeatResponse(
        heartbeatSnapshot.sample(),
        heartbeatSnapshot.checkpoint(),
        heartbeatSnapshot.nInputEventsTotal(),
        heartbeatSnapshot.nOutputEventsTotal());
  }
}
