package io.ulzha.spive.basicrunner.api;

import static io.ulzha.spive.basicrunner.matchers.HeartbeatSampleMatcher.progressUpdatesListThat;
import static io.ulzha.spive.basicrunner.matchers.ProgressUpdateMatcher.containsEssentially;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSnapshot;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

public class UmbilicalTest {
  @Test
  void givenUnknownEventTimeUpdates_getHeartbeatSnapshot_shouldReturnExpectedFirsts() {
    final Umbilical sut = new Umbilical();

    sut.addHeartbeat(null);
    final EventTime t0 = EventTime.fromString("2024-02-15T15:00:00Z#0");
    sut.addHeartbeat(t0);
    sut.addSuccess(t0);
    Exception e0 = new Exception("Concurrent workload exception, let's say");
    sut.addError(null, e0);

    final HeartbeatSnapshot actual = sut.getHeartbeatSnapshot(false);

    assertThat(actual.sample().size(), is(2));
    assertThat(
        actual.sample().get(0),
        progressUpdatesListThat(
            nullValue(EventTime.class),
            nullValue(),
            containsEssentially(new ProgressUpdate(), ProgressUpdate.createError(e0))));
    assertThat(
        actual.sample().get(1),
        progressUpdatesListThat(
            is(t0),
            nullValue(),
            containsEssentially(new ProgressUpdate(), ProgressUpdate.createSuccess())));
    assertThat(actual.nInputEventsHandled(), is(1L));
    assertThat(actual.nOutputEvents(), is(0L));
  }

  @Test
  void givenNominalUpdates_getHeartbeatSnapshot_shouldReturnExpectedFirstsAndCheckpoint() {
    final Umbilical sut = new Umbilical();

    sut.addHeartbeat(null);
    for (int i = 0; i < 5; i++) {
      final EventTime t = EventTime.fromString("2021-12-10T05:00:00Z#" + i);
      sut.addHeartbeat(t);
      sut.addOutputEvent(t);
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
            containsEssentially(new ProgressUpdate(), ProgressUpdate.createSuccess())));
    assertThat(actual.checkpoint(), is(t4));
    assertThat(actual.nOutputEvents(), is(5L));
  }

  @Test
  void
      givenNominalUpdateAndWarningUpdates_getHeartbeatSnapshot_shouldReturnExpectedFirstsAndCheckpoint() {
    final Umbilical sut = new Umbilical();

    sut.addHeartbeat(null);
    final EventTime t0 = EventTime.fromString("2024-02-15T15:00:00Z#0");
    sut.addHeartbeat(t0);
    sut.addSuccess(t0);
    final EventTime t1 = EventTime.fromString("2024-02-15T15:00:00Z#1");
    sut.addHeartbeat(t1);
    for (int i = 0; i < 5; i++) {
      sut.addWarning(t1, new Exception("Sending it"));
    }

    final HeartbeatSnapshot actual = sut.getHeartbeatSnapshot(false);

    assertThat(actual.sample().size(), is(2));
    assertThat(actual.sample().get(0).eventTime(), is(t0));
    assertThat(actual.sample().get(1).eventTime(), is(t1));
    assertThat(actual.checkpoint(), is(t0));
    assertThat(actual.nInputEventsHandled(), is(1L));
  }

  @Test
  void givenManyEventsWithNominalUpdates_getHeartbeatSnapshotVerbose_shouldBeTruncatedToTen() {
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
    assertThat(actual.nInputEventsHandled(), is(12L));
  }

  @Test
  void
      givenNominalUpdateAndManyEventsWithWarningUpdates_getHeartbeatSnapshot_shouldReturnExpectedFirstsAndCheckpoint() {
    final Umbilical sut = new Umbilical();

    sut.addHeartbeat(null);
    final EventTime t0 = EventTime.fromString("2024-02-15T15:00:00Z#0");
    sut.addHeartbeat(t0);
    sut.addSuccess(t0);
    for (int i = 1; i < 12; i++) {
      final EventTime t = EventTime.fromString("2024-02-15T15:00:00Z#" + i);
      sut.addHeartbeat(t);
      sut.addWarning(t, new Exception("Sending it"));
      sut.addSuccess(t);
    }

    final HeartbeatSnapshot actual = sut.getHeartbeatSnapshot(false);

    assertThat(actual.sample().size(), is(2));
    // we haven't really codified a good decision yet which warning(s) to keep
    final EventTime tLatestSuccess = EventTime.fromString("2024-02-15T15:00:00Z#11");
    assertThat(actual.sample().get(1).eventTime(), is(tLatestSuccess));
    assertThat(actual.checkpoint(), is(tLatestSuccess));
    assertThat(actual.nInputEventsHandled(), is(12L));
  }

  //  @Test
  //  void givenErrorUpdates_getHeartbeatSnapshot_shouldReturnExpectedFirstsAndCheckpoint() {
  //
  //  }

  //  @Test
  //  void givenNominalUpdates_getHeartbeatSnapshotVerbose_shouldReturnFullSnapshot() {
  //  }

  @Test
  void givenSomeIo_getIopwsList_shouldReturnEventCounts() {
    final Umbilical sut = new Umbilical();

    final Instant t0 = Instant.parse("2024-12-31T23:55:00Z");
    final Instant t1 = Instant.parse("2025-01-01T00:00:15Z");
    for (Instant t = t0; !t.equals(t1); t = t.plus(15, ChronoUnit.SECONDS)) {
      sut.aggregateIopw(t, 1);
      sut.addOutputEvent(new EventTime(t, 0));
    }

    var l1 = sut.getIopwsList(t0);
    assertThat(l1.size(), is(5));
    assertThat(l1.get(0).windowStart(), is(Instant.parse("2024-12-31T23:55:00Z")));
    assertThat(l1.get(0).windowEnd(), is(Instant.parse("2024-12-31T23:56:00Z")));
    assertThat(l1.get(0).nInputEventsHandledOk(), is(4L));
    assertThat(l1.get(0).nOutputEvents(), is(4L));
  }

  @Test
  void givenMuchIo_getIopwsList_shouldReturnOneDayAtATime() {
    final Umbilical sut = new Umbilical();

    final Instant t0 = Instant.parse("2022-12-31T22:53:00Z");
    final Instant t1 = Instant.parse("2023-01-02T00:07:00Z");
    for (Instant t = t0; !t.equals(t1); t = t.plus(15, ChronoUnit.SECONDS)) {
      if (t.atZone(ZoneOffset.UTC).getMinute() % 5 != 0) {
        sut.aggregateIopw(t, 1);
      }
    }

    var l1 = sut.getIopwsList(t0);
    assertThat(l1.size(), is(7 + 60));

    var l1m = sut.getIopwsList(t0.plus(7, ChronoUnit.MINUTES));
    assertThat(l1m.size(), is(60));

    final Instant l1End = l1.get(l1.size() - 1).windowEnd();
    assertThat(l1End, is(Instant.parse("2023-01-01T00:00:00Z")));

    var l2 = sut.getIopwsList(l1End);
    assertThat(l2.size(), is(24 * 60));

    final Instant l2End = l2.get(l2.size() - 1).windowEnd();
    assertThat(l2End, is(Instant.parse("2023-01-02T00:00:00Z")));

    var l3 = sut.getIopwsList(l2End);
    assertThat(l3.size(), is(6));

    final Instant l3End = l3.get(l3.size() - 1).windowEnd();
    assertThat(l3End, is(Instant.parse("2023-01-02T00:06:00Z")));

    var l4 = sut.getIopwsList(l3End);
    assertThat(l4.size(), is(0));

    var l = sut.getIopwsList(t0.atZone(ZoneOffset.UTC).minus(1, ChronoUnit.CENTURIES).toInstant());
    assertIterableEquals(l1, l);

    var lm = sut.getIopwsList(l3End.minus(1, ChronoUnit.MINUTES));
    assertThat(lm.size(), is(1));
  }
}
