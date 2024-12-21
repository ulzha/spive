package io.ulzha.spive.app.workloads.watchdog;

import io.ulzha.spive.app.model.Process;
import io.ulzha.spive.basicrunner.api.BasicRunnerClient;
import io.ulzha.spive.basicrunner.api.GetThreadGroupHeartbeatResponse;
import io.ulzha.spive.basicrunner.api.GetThreadGroupIopwsResponse;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSnapshot;
import io.ulzha.spive.lib.umbilical.HistoryBuffer;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import io.ulzha.spive.lib.umbilical.ProgressUpdatesList;
import io.ulzha.spive.lib.umbilical.UmbilicalReader;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.TreeMap;

/**
 * A counterpart to i.u.s.basicrunner.api.Umbilical.Umbilicus.
 *
 * <p>A "fat" client containing helper functionality to splice heartbeat samples from umbilical into
 * a longer history. TODO less fat? Just asynchronous buffering here. Ephemeral aggregation in
 * watchdog/trackers, and permanent aggregation in event handlers/compactors. Plus archival in
 * SpiveArchiver.
 */
// Common aggregation logic, same on runner and in control plane? Or no aggregation in runner?
// Potentially meaningless layers of interfaces... Why not BasicRunnerClient implements
// RunnerClient? UmbilicalChannel? Refactor into core.lib? Remove and keep only event-sourced state
// in Spive (Timeline), offload ephemeral best effort histories to SpiveScaler and friends?
// All instance level aggregations including Timeline maybe should live here?
public class Placenta implements UmbilicalReader {
  private final BasicRunnerClient client;
  // TODO cap
  private TreeMap<EventTime, List<ProgressUpdate>> accumulatedHeartbeat = new TreeMap<>();

  public Placenta(final HttpClient httpClient, final Process.Instance instance) {
    this.client = new BasicRunnerClient(httpClient, instance.umbilicalUri);
  }

  @Override
  public HeartbeatSnapshot updateHeartbeat() throws InterruptedException {
    final GetThreadGroupHeartbeatResponse response = client.getHeartbeat();

    // TODO validate sequences again?

    for (ProgressUpdatesList list : response.sample()) {
      if (list.eventTime() != null) {
        accumulatedHeartbeat.put(list.eventTime(), list.progressUpdates());
      }
      // FIXME accumulate updates instead of replacing
      // what about unknownEventTimeSample... Simplify silly indirection away and commonize types?
    }

    // maybe a bit silly indirection; keeping HeartbeatSnapshot class (common module as such) clean
    // of API/serde concerns
    // TODO generate? Other serialization formats like gRPC would provide just that, generated code
    return new HeartbeatSnapshot(
        response.sample(),
        response.checkpoint(),
        response.nInputEventsHandled(),
        response.nOutputEvents());
  }

  @Override
  public List<ProgressUpdate> get(EventTime t) {
    return accumulatedHeartbeat.get(t);
  }

  @Override
  public EventTime getNextEventTime(EventTime t) {
    return accumulatedHeartbeat.higherKey(t);
  }

  //  public List<ProgressUpdate> getNext(EventTime t) {
  //    final var entry = accumulatedHeartbeat.higherEntry(t);
  //    return (entry == null ? List.of() : entry.getValue());
  //  }

  @Override
  public List<HistoryBuffer.Iopw> updateIopws(Instant start) throws InterruptedException {
    final GetThreadGroupIopwsResponse response = client.getIopws(start);
    return response.iopws();
  }
}
