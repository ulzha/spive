package io.ulzha.spive.app.workloads.watchdog;

import io.ulzha.spive.app.model.Process;
import io.ulzha.spive.basicrunner.api.BasicRunnerClient;
import io.ulzha.spive.basicrunner.api.GetThreadGroupHeartbeatResponse;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSample;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import io.ulzha.spive.lib.umbilical.UmbilicalReader;
import java.net.http.HttpClient;
import java.util.List;
import java.util.TreeMap;

/**
 * A counterpart to i.u.s.basicrunner.api.Umbilical.Umbilicus.
 *
 * <p>A "fat" client containing helper functionality to splice heartbeat samples from umbilical into
 * a longer history.
 */
// UmbilicalChannel? Refactor into core.lib?
public class Placenta implements UmbilicalReader {
  private final BasicRunnerClient client;
  private TreeMap<EventTime, List<ProgressUpdate>> accumulatedHeartbeat = new TreeMap<>();

  public Placenta(final HttpClient httpClient, final Process.Instance instance) {
    this.client = new BasicRunnerClient(httpClient, instance.umbilicalUri);
  }

  @Override
  public void updateHeartbeat() throws InterruptedException {
    final GetThreadGroupHeartbeatResponse response = client.getHeartbeat();

    // TODO validate sequences

    for (HeartbeatSample sample : response.heartbeat()) {
      accumulatedHeartbeat.put(sample.eventTime(), sample.progressUpdates());
      // FIXME accumulate updates instead of replacing
    }
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

}
