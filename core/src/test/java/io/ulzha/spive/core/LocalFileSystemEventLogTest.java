package io.ulzha.spive.core;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventTime;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// TODO suite with identical scenarios, applied to all EventLog implementations consistently
public class LocalFileSystemEventLogTest {
  @Test
  public void
      givenCompetingEventHasEqualTimeDifferentPayload_whenPeekedViaIterator_thenNextReturnsActualEvent()
          throws Exception {
    final EventEnvelope e = dummyEvent(1);
    final Path filePath =
        Path.of(Objects.requireNonNull(getClass().getResource("TwoEvents.jsonl")).getPath());
    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final var iterator = eventLog.iterator();
      iterator.next();

      final EventEnvelope actual = iterator.appendOrPeek(e);
      assertThat(actual, not(e));
      assertThat(actual.time(), is(e.time()));
      assertThat(actual.serializedPayload(), not(e.serializedPayload()));
      // reference equality here is relevant (not `is`, which calls `equals`)
      assertTrue(actual != e);
      assertTrue(actual == iterator.next());
    }
  }

  @Test
  public void
      givenCompetingEventHasEqualTimeAndEqualPayload_whenPeekedViaIterator_thenNextReturnsActualEvent()
          throws Exception {
    final EventEnvelope e =
        new EventEnvelope(
            new EventTime(Instant.parse("1111-11-11T00:00:00Z"), 1),
            null,
            "pojo:io.ulzha.spive.test.DeleteProcess",
            "{\"processId\": \"00000000-0000-0000-0000-000000000000\"}");
    final Path filePath =
        Path.of(Objects.requireNonNull(getClass().getResource("TwoEvents.jsonl")).getPath());
    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final var iterator = eventLog.iterator();
      iterator.next();

      final EventEnvelope actual = iterator.appendOrPeek(e);
      assertThat(actual, is(e));
      // reference equality here is relevant (not `is`, which calls `equals`)
      assertTrue(actual != e);
      assertTrue(actual == iterator.next());
    }
  }

  @Test
  public void givenNoCompetingEvent_whenAppendedViaIterator_thenNextReturnsAppendedEvent()
      throws Exception {
    final EventEnvelope e = dummyEvent(2);
    final Path filePath =
        Path.of(Objects.requireNonNull(getClass().getResource("TwoEvents.jsonl")).getPath());
    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final var iterator = eventLog.iterator();
      iterator.next();
      iterator.next();

      final EventEnvelope actual = iterator.appendOrPeek(e);
      // reference equality here is relevant (not `is`, which calls `equals`)
      assertTrue(actual == e);
      assertTrue(actual == iterator.next());
    }
  }

  @Test
  public void givenEmptyLog_whenAppendedViaIterator_shouldReadExpectedEventAndBlock()
      throws Exception {
    final EventEnvelope e = dummyEvent(0);
    final Path filePath = emptyTempFile();
    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final var iterator = eventLog.iterator();

      final EventEnvelope actual = iterator.appendOrPeek(e);
      // reference equality here is relevant (not `is`, which calls `equals`)
      assertTrue(actual == e);
      assertTrue(actual == iterator.next());

      // Sic - this file has no closing marker, the logic is to expect more events eventually.
      Assertions.assertThrows(
          ConditionTimeoutException.class,
          () -> await().atMost(Duration.ofSeconds(1)).until(iterator::hasNext));
    }
  }

  @Test
  public void givenEmptyLog_whenAppended_shouldReadExpectedEventAndBlock() throws Exception {
    final EventEnvelope e = dummyEvent(0);
    final Path filePath = emptyTempFile();
    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final var iterator = eventLog.iterator();

      final boolean appended = eventLog.appendIfPrevTimeMatch(e, EventTime.INFINITE_PAST);
      assertTrue(appended);

      assertTrue(iterator.hasNext());
      final EventEnvelope eRead = iterator.next();
      assertThat(eRead.typeTag(), is("pojo:io.ulzha.spive.test.WhamProcess"));
      assertThat(eRead.serializedPayload(), is("\"WHAM!\""));

      // Sic - this file has no closing marker, the logic is to expect more events eventually.
      Assertions.assertThrows(
          ConditionTimeoutException.class,
          () -> await().atMost(Duration.ofSeconds(1)).until(iterator::hasNext));
    }
  }

  @Test
  public void givenUnclosedLog_whenReadTillTheEnd_shouldReadExpectedEventsAndBlock()
      throws Exception {
    final Path filePath =
        Path.of(Objects.requireNonNull(getClass().getResource("TwoEvents.jsonl")).getPath());
    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final var iterator = eventLog.iterator();

      assertTrue(iterator.hasNext());
      final EventEnvelope event1 = iterator.next();
      assertThat(event1.typeTag(), is("pojo:io.ulzha.spive.test.CreateProcess"));

      assertTrue(iterator.hasNext());
      final EventEnvelope event2 = iterator.next();
      assertThat(event2.typeTag(), is("pojo:io.ulzha.spive.test.DeleteProcess"));

      // Sic - this file has no closing marker, the logic is to expect more events eventually.
      Assertions.assertThrows(
          ConditionTimeoutException.class,
          () -> await().atMost(Duration.ofSeconds(1)).until(iterator::hasNext));
    }
  }

  @Test
  public void givenUnclosedLog_whenReadTillTheEndAndAppended_shouldReadExpectedEventsAndBlock()
      throws Exception {
    final Path filePath = copyResourceToTempFile("TwoEvents.jsonl");
    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final var iterator = eventLog.iterator();

      assertTrue(iterator.hasNext());
      final EventEnvelope event1 = iterator.next();
      assertThat(event1.typeTag(), is("pojo:io.ulzha.spive.test.CreateProcess"));

      assertTrue(iterator.hasNext());
      final EventEnvelope event2 = iterator.next();
      assertThat(event2.typeTag(), is("pojo:io.ulzha.spive.test.DeleteProcess"));

      final EventTime eventTime3 = new EventTime(Instant.parse("1111-11-11T00:00:00.111Z"), 0);
      final EventEnvelope event3 =
          new EventEnvelope(
              eventTime3, UUID.randomUUID(), "pojo:io.ulzha.spive.test.WhamProcess", "\"WHAM!\"");
      final boolean appended = eventLog.appendIfPrevTimeMatch(event3, event2.time());

      assertTrue(appended);

      assertTrue(iterator.hasNext());
      final EventEnvelope event3Read = iterator.next();
      assertThat(event3Read.typeTag(), is("pojo:io.ulzha.spive.test.WhamProcess"));
      assertThat(event3Read.serializedPayload(), is("\"WHAM!\""));

      // Sic - this file has no closing marker, the logic is to expect more events eventually.
      Assertions.assertThrows(
          ConditionTimeoutException.class,
          () -> await().atMost(Duration.ofSeconds(1)).until(iterator::hasNext));
    }
  }

  // @Test
  // public void givenOngoingAppendOfLargeEvent_whenReadConcurrently_shouldReadEntireEvent() {
  //   // TODO
  // }

  @Test
  public void givenClosedLog_whenReadTillTheEnd_shouldReadExpectedEventsAndNotBlock()
      throws Exception {
    final Path filePath =
        Path.of(Objects.requireNonNull(getClass().getResource("OneEventClosed.jsonl")).getPath());
    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final var iterator = eventLog.iterator();

      assertTrue(iterator.hasNext());
      final EventEnvelope event1 = iterator.next();
      assertThat(event1.typeTag(), is("pojo:io.ulzha.spive.test.CreateProcess"));

      assertFalse(iterator.hasNext());
    }
  }

  // Well this guard would incur overhead for KV stores, we don't want that I think
  // @Test
  // public void
  // givenTwoEventsInLog_whenAppendingSpuriouslyBeforeFirst_shouldDoNothingAndReturnFalse()
  //     throws Exception {
  // ...
  //     final EventTime eventTime1 = new EventTime(Instant.parse("1111-11-01T00:00:00Z"), 0);
  // ...
  //     final boolean appended = eventLog.appendIfPrevTimeMatch(event2, eventTime1);
  // ...
  // }

  @Test
  public void givenTwoEventsInLog_whenAppendingBeforeFirst_shouldDoNothingAndReturnFalse()
      throws Exception {
    final Path filePath = copyResourceToTempFile("TwoEvents.jsonl");
    final byte[] bytesOrig = Files.readAllBytes(filePath);

    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final EventTime eventTime1 = new EventTime(Instant.parse("1111-11-01T00:00:00Z"), 0);
      final EventEnvelope event1 =
          new EventEnvelope(
              eventTime1, UUID.randomUUID(), "pojo:io.ulzha.spive.test.InceptProcess", "\"EIEIO\"");
      final boolean appended = eventLog.appendIfPrevTimeMatch(event1, EventTime.INFINITE_PAST);
      assertFalse(appended);
    }

    final byte[] bytes = Files.readAllBytes(filePath);
    assertThat(bytes, is(bytesOrig));
  }

  @Test
  public void givenTwoEventsInLog_whenReadingAndAppendingAfterFirst_shouldDoNothingAndReturnFalse()
      throws Exception {
    final Path filePath = copyResourceToTempFile("TwoEvents.jsonl");
    final byte[] bytesOrig = Files.readAllBytes(filePath);

    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final var iterator = eventLog.iterator();

      assertTrue(iterator.hasNext());
      final EventEnvelope event1 = iterator.next();
      final EventEnvelope event2 =
          new EventEnvelope(
              new EventTime(Instant.parse("1111-11-11T00:00:00Z"), 1),
              UUID.randomUUID(),
              "pojo:io.ulzha.spive.test.DeleteProcess",
              "\"BRRRRR\"");
      final boolean appended = eventLog.appendIfPrevTimeMatch(event2, event1.time());

      assertFalse(appended);
      assertTrue(iterator.hasNext());
    }

    final byte[] bytes = Files.readAllBytes(filePath);
    assertThat(bytes, is(bytesOrig));
  }

  @Test
  public void givenTwoEventsInLog_whenAppendingTwoMore_shouldAppendAndReturnTrue()
      throws Exception {
    final Path filePath = copyResourceToTempFile("TwoEvents.jsonl");
    final byte[] bytesOrig = Files.readAllBytes(filePath);

    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final EventTime eventTime2 = new EventTime(Instant.parse("1111-11-11T00:00:00Z"), 1);
      final EventTime eventTime3 = new EventTime(Instant.parse("1111-11-11T00:00:00.111Z"), 0);
      final EventTime eventTime4 = new EventTime(Instant.parse("1111-11-11T00:00:00.111Z"), 1);
      final EventEnvelope event3 =
          new EventEnvelope(
              eventTime3, UUID.randomUUID(), "pojo:io.ulzha.spive.test.MournProcess", "\"BRRRRR\"");
      final boolean appended3 = eventLog.appendIfPrevTimeMatch(event3, eventTime2);

      assertTrue(appended3);

      final EventEnvelope event4 =
          new EventEnvelope(
              eventTime4, UUID.randomUUID(), "pojo:io.ulzha.spive.test.MournProcess", "\"BZZZZZ\"");
      final boolean appended4 = eventLog.appendIfPrevTimeMatch(event4, eventTime3);

      assertTrue(appended4);
    }

    final byte[] bytes = Files.readAllBytes(filePath);
    assertTrue(bytes.length > bytesOrig.length);
    assertThat(Arrays.copyOfRange(bytes, 0, bytesOrig.length), is(bytesOrig));
  }

  @Test
  public void whenAppendingSameTimeAsPrevTime_shouldThrow() throws Exception {
    final Path filePath = copyResourceToTempFile("TwoEvents.jsonl");
    final byte[] bytesOrig = Files.readAllBytes(filePath);

    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final EventTime eventTime2 = new EventTime(Instant.parse("1111-11-11T00:00:00Z"), 1);
      final EventEnvelope event2 =
          new EventEnvelope(
              eventTime2, UUID.randomUUID(), "pojo:io.ulzha.spive.test.InceptProcess", "\"EIEIO\"");
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> eventLog.appendIfPrevTimeMatch(event2, eventTime2));
    }

    final byte[] bytes = Files.readAllBytes(filePath);
    assertThat(bytes, is(bytesOrig));
  }

  @Test
  public void givenClosedLog_whenAppendingOneMore_shouldThrow() throws Exception {
    final Path filePath = copyResourceToTempFile("OneEventClosed.jsonl");
    final byte[] bytesOrig = Files.readAllBytes(filePath);

    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final EventTime eventTime1 = new EventTime(Instant.parse("1111-11-11T00:00:00Z"), 0);
      final EventTime eventTime2 = new EventTime(Instant.parse("1111-11-11T00:00:00Z"), 1);
      final EventEnvelope event2 =
          new EventEnvelope(
              eventTime2,
              UUID.randomUUID(),
              "pojo:io.ulzha.spive.test.DeleteProcess",
              "\"BRRRRR\"");
      Assertions.assertThrows(
          IllegalStateException.class, () -> eventLog.appendIfPrevTimeMatch(event2, eventTime1));
    }

    final byte[] bytes = Files.readAllBytes(filePath);
    assertThat(bytes, is(bytesOrig));
  }

  private EventEnvelope dummyEvent(int i) {
    return new EventEnvelope(
        new EventTime(Instant.parse("1111-11-11T00:00:00Z"), i),
        null,
        "pojo:io.ulzha.spive.test.WhamProcess",
        "\"WHAM!\"");
  }

  private Path copyResourceToTempFile(final String name) throws IOException {
    final Path filePathOrig =
        Path.of(Objects.requireNonNull(getClass().getResource(name)).getPath());

    final File file = File.createTempFile("spive_test_", "__" + name);
    file.deleteOnExit();
    final Path filePath = file.toPath();
    Files.copy(filePathOrig, filePath, StandardCopyOption.REPLACE_EXISTING);

    return filePath;
  }

  private Path emptyTempFile() throws IOException {
    final File file = File.createTempFile("spive_test_", "__Events.jsonl");
    file.deleteOnExit();
    final Path filePath = file.toPath();

    return filePath;
  }
}
