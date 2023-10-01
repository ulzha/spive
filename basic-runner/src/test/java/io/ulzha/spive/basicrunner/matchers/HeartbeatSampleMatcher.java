package io.ulzha.spive.basicrunner.matchers;

import static com.spotify.hamcrest.pojo.IsPojo.pojo;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import io.ulzha.spive.lib.umbilical.ProgressUpdatesList;
import java.util.List;
import org.hamcrest.Matcher;

public class HeartbeatSampleMatcher {
  public static Matcher<ProgressUpdatesList> progressUpdatesListThat(
      Matcher<EventTime> eventTimeMatcher,
      Matcher<Object> partitionMatcher,
      Matcher<List<ProgressUpdate>> progressUpdatesMatcher) {
    return pojo(ProgressUpdatesList.class)
        .where(ProgressUpdatesList::eventTime, eventTimeMatcher)
        .where(ProgressUpdatesList::partition, partitionMatcher)
        .where(ProgressUpdatesList::progressUpdates, progressUpdatesMatcher);
  }
}
