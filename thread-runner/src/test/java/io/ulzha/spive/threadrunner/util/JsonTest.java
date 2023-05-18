package io.ulzha.spive.threadrunner.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSample;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import io.ulzha.spive.threadrunner.api.GetThreadGroupHeartbeatResponse;
import jakarta.json.bind.Jsonb;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class JsonTest {
  private Jsonb jsonb = Json.create();

  @Test
  void testRoundTripGetThreadGroupHeartbeatResponseUnknownTimes() {
    final List<ProgressUpdate> updates = new ArrayList<>();
    updates.add(new ProgressUpdate(Instant.parse("2021-01-21T19:00:01Z"), false, null, "ARRRR"));
    final List<HeartbeatSample> heartbeat = new ArrayList<>();
    heartbeat.add(new HeartbeatSample(null, null, updates));
    final GetThreadGroupHeartbeatResponse r = new GetThreadGroupHeartbeatResponse(heartbeat, null);

    assertRoundTrip(
        r,
        "{\"heartbeat\":[{\"eventTime\":null,\"partition\":null,\"progressUpdates\":[{\"instant\":\"2021-01-21T19:00:01Z\",\"error\":\"ARRRR\"}]}],\"checkpoint\":null}",
        GetThreadGroupHeartbeatResponse.class);
  }

  @Test
  void testRoundTripGetThreadGroupHeartbeatResponse() {
    final EventTime t1 = EventTime.fromString("2021-01-21T19:00:00.123456789Z#0");
    final List<ProgressUpdate> updates = new ArrayList<>();
    final List<HeartbeatSample> heartbeat = new ArrayList<>();
    heartbeat.add(new HeartbeatSample(t1, null, updates));
    final GetThreadGroupHeartbeatResponse r = new GetThreadGroupHeartbeatResponse(heartbeat, t1);

    assertRoundTrip(
        r,
        "{\"heartbeat\":[{\"eventTime\":\"2021-01-21T19:00:00.123456789Z#0\",\"partition\":null,\"progressUpdates\":[]}],\"checkpoint\":\"2021-01-21T19:00:00.123456789Z#0\"}",
        GetThreadGroupHeartbeatResponse.class);

    updates.add(new ProgressUpdate(Instant.parse("2021-01-21T19:00:01Z"), false, null, null));
    updates.add(new ProgressUpdate(Instant.parse("2021-01-21T19:00:02Z"), true, null, null));
    assertRoundTrip(
        r,
        "{\"heartbeat\":[{\"eventTime\":\"2021-01-21T19:00:00.123456789Z#0\",\"partition\":null,\"progressUpdates\":[{\"instant\":\"2021-01-21T19:00:01Z\"},{\"instant\":\"2021-01-21T19:00:02Z\",\"success\":true}]}],\"checkpoint\":\"2021-01-21T19:00:00.123456789Z#0\"}",
        GetThreadGroupHeartbeatResponse.class);

    final EventTime t2 = EventTime.fromString("2021-01-29T12:34:56.789Z#13");
    final List<ProgressUpdate> updates2 =
        List.of(
            new ProgressUpdate(Instant.parse("2021-01-29T12:34:57.789Z"), false, null, null),
            new ProgressUpdate(
                Instant.parse("2021-01-29T12:34:58.789Z"),
                false,
                null,
                "java.lang.RuntimeException\n\tat io.ulzha.spive.Dummy(Dummy.java:42)"));
    r.heartbeat().add(new HeartbeatSample(t2, null, updates2));
    assertRoundTrip(
        r,
        "{\"heartbeat\":[{\"eventTime\":\"2021-01-21T19:00:00.123456789Z#0\",\"partition\":null,\"progressUpdates\":[{\"instant\":\"2021-01-21T19:00:01Z\"},{\"instant\":\"2021-01-21T19:00:02Z\",\"success\":true}]},{\"eventTime\":\"2021-01-29T12:34:56.789Z#13\",\"partition\":null,\"progressUpdates\":[{\"instant\":\"2021-01-29T12:34:57.789Z\"},{\"instant\":\"2021-01-29T12:34:58.789Z\",\"error\":\"java.lang.RuntimeException\\n\\tat io.ulzha.spive.Dummy(Dummy.java:42)\"}]}],\"checkpoint\":\"2021-01-21T19:00:00.123456789Z#0\"}",
        GetThreadGroupHeartbeatResponse.class);
  }

  private <T> void assertRoundTrip(T x, String s, Class<?> xClass) {
    assertEquals(s, jsonb.toJson(x));
    assertEquals(x, jsonb.fromJson(s, xClass));
  }
}
