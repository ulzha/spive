package io.ulzha.spive.basicrunner.api;

import static io.ulzha.spive.basicrunner.matchers.HeartbeatSampleMatcher.progressUpdatesListThat;
import static io.ulzha.spive.basicrunner.matchers.ProgressUpdateMatcher.containsIgnoringTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.*;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSnapshot;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import org.junit.jupiter.api.Test;

public class UmbilicalTest {
  @Test
  void givenNominalUpdates_getHeartbeatSnapshot_shouldReturnExpectedFirstsAndCheckpoint() {
    final Umbilical sut = new Umbilical();

    sut.addHeartbeat(null);
    for (int i = 0; i < 5; i++) {
      final EventTime t = EventTime.fromString("2021-12-10T05:00:00Z#" + i);
      sut.addHeartbeat(t);
      sut.addSuccess(t);
    }

    final HeartbeatSnapshot actual = sut.getHeartbeatSnapshot(false);

    final EventTime t4 = EventTime.fromString("2021-12-10T05:00:00Z#4");
    assertThat(actual.sample().size(), is(1));
    assertThat(
        actual.sample().get(0),
        progressUpdatesListThat(
            is(t4),
            nullValue(),
            containsIgnoringTime(new ProgressUpdate(), ProgressUpdate.createSuccess())));
    assertThat(actual.checkpoint(), is(t4));
    assertThat(actual.nInputEventsTotal(), equalTo(5L));
  }

  @Test
  void givenManyUpdates_getHeartbeatSnapshotVerbose_shouldBeTruncatedToTen() {
    final Umbilical sut = new Umbilical();

    sut.addHeartbeat(null);
    for (int i = 0; i < 12; i++) {
      final EventTime t = EventTime.fromString("2021-12-10T12:00:00Z#" + i);
      sut.addHeartbeat(t);
      sut.addSuccess(t);
    }

    final HeartbeatSnapshot actual = sut.getHeartbeatSnapshot(true);

    assertThat(actual.sample().size(), is(10));
    assertNull(actual.sample().get(0).eventTime());
    assertThat(
        actual.sample().get(1).eventTime(), is(EventTime.fromString("2021-12-10T12:00:00Z#3")));
    assertThat(
        actual.sample().get(9).eventTime(), is(EventTime.fromString("2021-12-10T12:00:00Z#11")));
    assertThat(actual.checkpoint(), is(EventTime.fromString("2021-12-10T12:00:00Z#11")));
    assertThat(actual.nInputEventsTotal(), equalTo(12L));
  }

  //  @Test
  //  void givenErrorUpdates_getHeartbeatSnapshot_shouldReturnExpectedFirstsAndCheckpoint() {
  //
  //  }

  //  @Test
  //  void givenNominalUpdates_getHeartbeatSnapshotVerbose_shouldReturnFullSnapshot() {
  //  }
}
