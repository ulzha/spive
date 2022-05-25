package io.ulzha.spive.app.workloads.watchdog;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import io.ulzha.spive.lib.umbilical.UmbilicalReader;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class FakePlacenta implements UmbilicalReader {
  private TreeMap<EventTime, List<ProgressUpdate>> accumulatedHeartbeat = new TreeMap<>();

  public void givenHeartbeat(EventTime eventTime, ProgressUpdate update) {
    accumulatedHeartbeat.computeIfAbsent(eventTime, k -> new ArrayList<>()).add(update);
  }

  @Override
  public void updateHeartbeat() {}

  @Override
  public List<ProgressUpdate> get(final EventTime t) {
    return accumulatedHeartbeat.get(t);
  }

  @Override
  public EventTime getNextEventTime(final EventTime t) {
    return accumulatedHeartbeat.higherKey(t);
  }
}
