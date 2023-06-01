package io.ulzha.spive.basicrunner.matchers;

import static com.spotify.hamcrest.pojo.IsPojo.pojo;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSample;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import java.util.List;
import org.hamcrest.Matcher;

public class HeartbeatSampleMatcher {
  public static Matcher<HeartbeatSample> heartbeatSampleThat(
      Matcher<EventTime> eventTimeMatcher,
      Matcher<Object> partitionMatcher,
      Matcher<List<ProgressUpdate>> progressUpdatesMatcher) {
    return pojo(HeartbeatSample.class)
        .where(HeartbeatSample::eventTime, eventTimeMatcher)
        .where(HeartbeatSample::partition, partitionMatcher)
        .where(HeartbeatSample::progressUpdates, progressUpdatesMatcher);
  }
}
