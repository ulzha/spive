package io.ulzha.spive;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ulzha.spive.core.LocalFileSystemEventLog;
import io.ulzha.spive.lib.EventEnvelope;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LocalFileSystemEventLogTest {
  static final ClassLoader loader = LocalFileSystemEventLogTest.class.getClassLoader();

  @Test
  public void givenUnclosedLog_whenReadTillTheEnd_shouldReadExpectedEventsAndBlock()
      throws Exception {
    final Path filePath =
        Path.of(Objects.requireNonNull(loader.getResource("TwoEvents.jsonl")).getPath());
    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final Iterator<EventEnvelope> iterator = eventLog.iterator();

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
  public void givenClosedLog_whenReadTillTheEnd_shouldReadExpectedEventsAndNotBlock()
      throws Exception {
    final Path filePath =
        Path.of(Objects.requireNonNull(loader.getResource("OneEventClosed.jsonl")).getPath());
    try (LocalFileSystemEventLog eventLog = new LocalFileSystemEventLog(filePath)) {
      final Iterator<EventEnvelope> iterator = eventLog.iterator();

      assertTrue(iterator.hasNext());
      final EventEnvelope event1 = iterator.next();
      assertThat(event1.typeTag(), is("pojo:io.ulzha.spive.test.CreateProcess"));

      assertFalse(iterator.hasNext());
    }
  }

  // @Test
  // public void testAppend() {}

  // @Test
  // public void testAppendIfPrevTimeMatch() {}
}
