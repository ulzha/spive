package io.ulzha.spive.threadrunner.util.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.umbilical.HeartbeatSample;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import io.ulzha.spive.threadrunner.api.GetThreadGroupHeartbeatResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ThreadRunnerModuleTest {
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new ThreadRunnerModule());
  }

  @Test
  void testRoundTripGetThreadGroupHeartbeatResponseUnknownTimes() throws JsonProcessingException {
    final List<ProgressUpdate> updates = new ArrayList<>();
    updates.add(ProgressUpdate.create(Instant.parse("2021-01-21T19:00:01Z"), false, null, "ARRRR"));
    final List<HeartbeatSample> heartbeat = new ArrayList<>();
    heartbeat.add(HeartbeatSample.create(null, null, updates));
    final GetThreadGroupHeartbeatResponse r =
        GetThreadGroupHeartbeatResponse.create(heartbeat, null);

    assertRoundTrip(
        r,
        "{\"heartbeat\":[{\"eventTime\":null,\"partition\":null,\"progressUpdates\":[{\"instant\":\"2021-01-21T19:00:01Z\",\"error\":\"ARRRR\"}]}],\"checkpoint\":null}",
        GetThreadGroupHeartbeatResponse.class);
  }

  @Test
  void testRoundTripGetThreadGroupHeartbeatResponse() throws JsonProcessingException {
    final EventTime t1 = EventTime.fromString("2021-01-21T19:00:00.123456789Z#0");
    final List<ProgressUpdate> updates = new ArrayList<>();
    final List<HeartbeatSample> heartbeat = new ArrayList<>();
    heartbeat.add(HeartbeatSample.create(t1, null, updates));
    final GetThreadGroupHeartbeatResponse r = GetThreadGroupHeartbeatResponse.create(heartbeat, t1);

    assertRoundTrip(
        r,
        "{\"heartbeat\":[{\"eventTime\":\"2021-01-21T19:00:00.123456789Z#0\",\"partition\":null,\"progressUpdates\":[]}],\"checkpoint\":\"2021-01-21T19:00:00.123456789Z#0\"}",
        GetThreadGroupHeartbeatResponse.class);

    updates.add(ProgressUpdate.create(Instant.parse("2021-01-21T19:00:01Z"), false, null, null));
    updates.add(ProgressUpdate.create(Instant.parse("2021-01-21T19:00:02Z"), true, null, null));
    assertRoundTrip(
        r,
        "{\"heartbeat\":[{\"eventTime\":\"2021-01-21T19:00:00.123456789Z#0\",\"partition\":null,\"progressUpdates\":[{\"instant\":\"2021-01-21T19:00:01Z\"},{\"instant\":\"2021-01-21T19:00:02Z\",\"success\":true}]}],\"checkpoint\":\"2021-01-21T19:00:00.123456789Z#0\"}",
        GetThreadGroupHeartbeatResponse.class);

    final EventTime t2 = EventTime.fromString("2021-01-29T12:34:56.789Z#13");
    final List<ProgressUpdate> updates2 =
        List.of(
            ProgressUpdate.create(Instant.parse("2021-01-29T12:34:57.789Z"), false, null, null),
            ProgressUpdate.create(
                Instant.parse("2021-01-29T12:34:58.789Z"),
                false,
                null,
                "java.lang.RuntimeException\n\tat io.ulzha.spive.Dummy(Dummy.java:42)"));
    r.heartbeat().add(HeartbeatSample.create(t2, null, updates2));
    assertRoundTrip(
        r,
        "{\"heartbeat\":[{\"eventTime\":\"2021-01-21T19:00:00.123456789Z#0\",\"partition\":null,\"progressUpdates\":[{\"instant\":\"2021-01-21T19:00:01Z\"},{\"instant\":\"2021-01-21T19:00:02Z\",\"success\":true}]},{\"eventTime\":\"2021-01-29T12:34:56.789Z#13\",\"partition\":null,\"progressUpdates\":[{\"instant\":\"2021-01-29T12:34:57.789Z\"},{\"instant\":\"2021-01-29T12:34:58.789Z\",\"error\":\"java.lang.RuntimeException\\n\\tat io.ulzha.spive.Dummy(Dummy.java:42)\"}]}],\"checkpoint\":\"2021-01-21T19:00:00.123456789Z#0\"}",
        GetThreadGroupHeartbeatResponse.class);
  }

  private <T> void assertRoundTrip(T x, String s, Class<?> xClass) throws JsonProcessingException {
    assertEquals(s, objectMapper.writeValueAsString(x));
    assertEquals(x, objectMapper.readValue(s, xClass));
  }
}
