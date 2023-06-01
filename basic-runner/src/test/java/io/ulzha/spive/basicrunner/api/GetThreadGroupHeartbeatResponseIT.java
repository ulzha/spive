package io.ulzha.spive.basicrunner.api;

import static io.ulzha.spive.basicrunner.matchers.HeartbeatSampleMatcher.heartbeatSampleThat;
import static io.ulzha.spive.basicrunner.matchers.ProgressUpdateMatcher.containsIgnoringTime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import org.junit.jupiter.api.Test;

public class GetThreadGroupHeartbeatResponseIT {
  @Test
  void givenNominalUpdates_responseCreate_shouldReturnExpectedFirstsAndCheckpoint() {
    final Umbilical sut = new Umbilical();

    sut.addHeartbeat(null);
    for (int i = 0; i < 5; i++) {
      final EventTime t = EventTime.fromString("2021-12-10T05:00:00Z#" + i);
      sut.addHeartbeat(t);
      sut.addSuccess(t);
    }

    final GetThreadGroupHeartbeatResponse actual = GetThreadGroupHeartbeatResponse.create(sut);

    final EventTime t4 = EventTime.fromString("2021-12-10T05:00:00Z#4");
    assertThat(actual.heartbeat().size(), is(1));
    assertThat(
        actual.heartbeat().get(0),
        heartbeatSampleThat(
            is(t4),
            nullValue(),
            containsIgnoringTime(new ProgressUpdate(), ProgressUpdate.createSuccess())));
    assertThat(actual.checkpoint(), is(t4));
  }

  //  @Test
  //  void givenErrorUpdates_responseCreate_shouldReturnExpectedFirstsAndCheckpoint() {
  //
  //  }

  //  @Test
  //  void givenNominalUpdates_responseCreateVerbose_shouldReturnFullSnapshot() {
  //  }
}
