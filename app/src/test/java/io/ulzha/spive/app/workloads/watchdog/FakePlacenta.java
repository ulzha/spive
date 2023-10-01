package io.ulzha.spive.app.workloads.watchdog;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSnapshot;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import io.ulzha.spive.lib.umbilical.UmbilicalReader;
import java.util.List;
import java.util.TreeMap;

public class FakePlacenta implements UmbilicalReader {
  private HeartbeatSnapshot snapshot;
  private TreeMap<EventTime, List<ProgressUpdate>> accumulatedHeartbeat = new TreeMap<>();

  public void givenHeartbeatSnapshot(final HeartbeatSnapshot snapshot) {
    this.snapshot = snapshot;
  }

  @Override
  public HeartbeatSnapshot updateHeartbeat() {
    for (var list : snapshot.sample()) {
      accumulatedHeartbeat.put(list.eventTime(), list.progressUpdates());
    }
    return snapshot;
  }

  @Override
  public List<ProgressUpdate> get(final EventTime t) {
    return accumulatedHeartbeat.get(t);
  }

  @Override
  public EventTime getNextEventTime(final EventTime t) {
    return accumulatedHeartbeat.higherKey(t);
  }
}
