package io.ulzha.spive.util.jackson;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ulzha.spive.lib.EventTime;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpiveModuleTest {
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new SpiveModule());
  }

  @Test
  void testRoundTripEventTime() throws JsonProcessingException {
    assertRoundTrip(
        new EventTime(Instant.ofEpochSecond(1611257224, 10_000_000), 2),
        "\"2021-01-21T19:27:04.010Z#2\"",
        EventTime.class);
  }

  private <T> void assertRoundTrip(T x, String s, Class<?> xClass) throws JsonProcessingException {
    assertEquals(s, objectMapper.writeValueAsString(x));
    assertEquals(x, objectMapper.readValue(s, xClass));
  }
}
