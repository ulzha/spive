package io.ulzha.spive.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventTime;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class JsonTest {
  @Test
  void testRoundTripEventMetadataWithoutPayload() throws IOException {
    EventEnvelope event =
        new EventEnvelope(
            new EventTime(Instant.ofEpochSecond(1611257224, 10_000_000), 2),
            UUID.fromString("1-3-5-7-9"),
            "pojr:ot.Hercule",
            null);
    String s = Json.serializeEventMetadata(event);
    assertEquals(
        "{\"id\":\"00000001-0003-0005-0007-000000000009\",\"time\":\"2021-01-21T19:27:04.010Z#2\",\"type\":\"pojr:ot.Hercule\"}",
        s);
    assertEquals(event, Json.deserializeEventMetadata(s, null));
  }
}
