package io.ulzha.spive.basicrunner.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSample;
import java.util.List;
import org.junit.jupiter.api.Test;

public class UmbilicalTest {
  @Test
  void givenManyUpdates_getHeartbeatSnapshot_shouldBeTruncatedToTen() {
    final Umbilical sut = new Umbilical();

    sut.addHeartbeat(null);
    for (int i = 0; i < 12; i++) {
      final EventTime t = EventTime.fromString("2021-12-10T12:00:00Z#" + i);
      sut.addHeartbeat(t);
      sut.addSuccess(t);
    }

    final List<HeartbeatSample> actual = sut.getHeartbeatSnapshot();

    assertThat(actual.size(), is(10));
    assertNull(actual.get(0).eventTime());
    assertThat(actual.get(1).eventTime(), is(EventTime.fromString("2021-12-10T12:00:00Z#3")));
    assertThat(actual.get(9).eventTime(), is(EventTime.fromString("2021-12-10T12:00:00Z#11")));
  }
}
