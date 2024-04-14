package io.ulzha.spive.core;

import static com.google.cloud.bigtable.data.v2.models.Filters.FILTERS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.cloud.bigtable.admin.v2.BigtableTableAdminClient;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminSettings;
import com.google.cloud.bigtable.admin.v2.models.CreateTableRequest;
import com.google.cloud.bigtable.admin.v2.models.GCRules;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowCell;
import com.google.cloud.bigtable.data.v2.models.RowMutation;
import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventIterator;
import io.ulzha.spive.lib.EventTime;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class BigtableEventLogTest {
  static final String PROJECT_ID = "test-project";
  static final String INSTANCE_ID = "test-instance";
  static final Integer BIGTABLE_EMULATOR_PORT = 8086;

  @Container
  public static final GenericContainer<?> bigtableEmulator =
      new GenericContainer<>("google/cloud-sdk")
          .withCommand(
              "gcloud",
              "beta",
              "emulators",
              "bigtable",
              "start",
              "--host-port",
              "0.0.0.0:" + BIGTABLE_EMULATOR_PORT)
          .withExposedPorts(BIGTABLE_EMULATOR_PORT);

  private static BigtableDataClient testDataClient;
  private static BigtableTableAdminClient testAdminClient;

  @BeforeAll
  public static void setupClass() throws IOException {
    testDataClient =
        BigtableDataClient.create(
            BigtableDataSettings.newBuilderForEmulator(
                    bigtableEmulator.getMappedPort(BIGTABLE_EMULATOR_PORT))
                .setProjectId(PROJECT_ID)
                .setInstanceId(INSTANCE_ID)
                .build());
    testAdminClient =
        BigtableTableAdminClient.create(
            BigtableTableAdminSettings.newBuilderForEmulator(
                    bigtableEmulator.getMappedPort(BIGTABLE_EMULATOR_PORT))
                .setProjectId(PROJECT_ID)
                .setInstanceId(INSTANCE_ID)
                .build());
    testAdminClient.createTable(
        CreateTableRequest.of("event-store").addFamily("event", GCRules.GCRULES.maxVersions(1)));
  }

  @Test
  public void
      givenCompetingEventHasEqualTimeDifferentPayload_whenPeekedViaIterator_thenNextReturnsActualEvent()
          throws Exception {
    final EventEnvelope e = dummyEvent(1);
    final UUID logId = copyTwoEventsToTempLog();
    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
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
    final UUID logId = copyTwoEventsToTempLog();
    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
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
    final UUID logId = copyTwoEventsToTempLog();
    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
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
  public void givenEmptyLog_whenAppendedViaIterator_shouldReadExpectedEventAndBlock() {
    final EventEnvelope e = dummyEvent(0);
    final UUID logId = emptyTempLog();
    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
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
  public void givenEmptyLog_whenAppended_shouldReadExpectedEventAndBlock() {
    final EventEnvelope e = dummyEvent(0);
    final UUID logId = emptyTempLog();
    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
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
    final UUID logId = copyTwoEventsToTempLog();
    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
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
  public void givenIteratorBlocked_whenAppendedViaLog_shouldUnblockAndReadExpectedEvent()
      throws Exception {
    final UUID logId = copyTwoEventsToTempLog();
    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
      final CountDownLatch latch = new CountDownLatch(2);
      final ExecutorService executor = Executors.newSingleThreadExecutor();
      final Future<?> act =
          executor.submit(
              () -> {
                final var iterator = eventLog.iterator();

                iterator.next();
                iterator.next();
                latch.countDown();

                final EventEnvelope event3Read = iterator.next();
                latch.countDown();

                assertThat(event3Read.typeTag(), is("pojo:io.ulzha.spive.test.WhamProcess"));
                assertThat(event3Read.serializedPayload(), is("\"WHAM!\""));
              });

      await().atMost(Duration.ofSeconds(1)).until(latch::getCount, is(1L));
      // assert blocking to expect more events, after two were read
      Assertions.assertThrows(
          ConditionTimeoutException.class,
          () -> await().atMost(Duration.ofSeconds(1)).until(latch::getCount, is(0L)));

      final EventTime eventTime2 = new EventTime(Instant.parse("1111-11-11T00:00:00Z"), 1);
      final EventTime eventTime3 = new EventTime(Instant.parse("1111-11-11T00:00:00.111Z"), 0);
      final EventEnvelope event3 =
          new EventEnvelope(
              eventTime3, UUID.randomUUID(), "pojo:io.ulzha.spive.test.WhamProcess", "\"WHAM!\"");
      final boolean appended = eventLog.appendIfPrevTimeMatch(event3, eventTime2);
      assertTrue(appended);

      act.get(2, TimeUnit.SECONDS);
    }
  }

  @Test
  public void
      givenEventIteratorBlocked_whenAppendedViaEventIterator_shouldUnblockAndReadExpectedEvent()
          throws Exception {
    final UUID logId = copyTwoEventsToTempLog();
    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
      final EventIterator eventIterator = new EventIterator(eventLog.iterator());

      final CountDownLatch latch = new CountDownLatch(2);
      final ExecutorService executor = Executors.newSingleThreadExecutor();
      final Future<?> act =
          executor.submit(
              () -> {
                eventIterator.next();
                eventIterator.next();
                latch.countDown();

                final EventEnvelope event3Read = eventIterator.next();
                latch.countDown();

                assertThat(event3Read.typeTag(), is("pojo:io.ulzha.spive.test.WhamProcess"));
                assertThat(event3Read.serializedPayload(), is("\"WHAM!\""));
              });

      await().atMost(Duration.ofSeconds(1)).until(latch::getCount, is(1L));
      // assert blocking to expect more events, after two were read
      Assertions.assertThrows(
          ConditionTimeoutException.class,
          () -> await().atMost(Duration.ofSeconds(1)).until(latch::getCount, is(0L)));

      final EventTime eventTime3 = new EventTime(Instant.parse("1111-11-11T00:00:00.111Z"), 0);
      final EventEnvelope event3 =
          new EventEnvelope(
              eventTime3, UUID.randomUUID(), "pojo:io.ulzha.spive.test.WhamProcess", "\"WHAM!\"");
      final EventEnvelope event3Peeked = eventIterator.appendOrPeek(event3);
      assertTrue(event3Peeked == event3);

      // this time it must be fast. Perhaps also whenAppendedViaLog?
      act.get(1, TimeUnit.MILLISECONDS);
    }
  }

  @Test
  public void givenUnclosedLog_whenReadTillTheEndAndAppended_shouldReadExpectedEventsAndBlock()
      throws Exception {
    final UUID logId = copyTwoEventsToTempLog();
    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
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
    final UUID logId = copyOneEventClosedToTempLog();
    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
      final var iterator = eventLog.iterator();

      assertTrue(iterator.hasNext());
      final EventEnvelope event1 = iterator.next();
      assertThat(event1.typeTag(), is("pojo:io.ulzha.spive.test.CreateProcess"));

      assertFalse(iterator.hasNext());
    }
  }

  // TODO some sort of guard against spurious buggy appends at random prevTime... But low overhead

  @Test
  public void givenTwoEventsInLog_whenAppendingBeforeFirst_shouldDoNothingAndReturnFalse()
      throws Exception {
    final UUID logId = copyTwoEventsToTempLog();
    final List<RowDumpEntry> dumpOrig = dumpAllRows(logId);

    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
      final EventTime eventTime1 = new EventTime(Instant.parse("1111-11-01T00:00:00Z"), 0);
      final EventEnvelope event1 =
          new EventEnvelope(
              eventTime1, UUID.randomUUID(), "pojo:io.ulzha.spive.test.InceptProcess", "\"EIEIO\"");
      final boolean appended = eventLog.appendIfPrevTimeMatch(event1, EventTime.INFINITE_PAST);
      assertFalse(appended);
    }

    final List<RowDumpEntry> dump = dumpAllRows(logId);
    assertThat(dump, is(dumpOrig));
  }

  @Test
  public void givenTwoEventsInLog_whenReadingAndAppendingAfterFirst_shouldDoNothingAndReturnFalse()
      throws Exception {
    final UUID logId = copyTwoEventsToTempLog();
    final List<RowDumpEntry> dumpOrig = dumpAllRows(logId);

    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
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

    final List<RowDumpEntry> dump = dumpAllRows(logId);
    assertThat(dump, is(dumpOrig));
  }

  @Test
  public void givenTwoEventsInLog_whenAppendingTwoMore_shouldAppendAndReturnTrue()
      throws Exception {
    final UUID logId = copyTwoEventsToTempLog();
    final List<RowDumpEntry> dumpOrig = dumpAllRows(logId);

    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
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

    final List<RowDumpEntry> dump = dumpAllRows(logId);
    assertTrue(dump.size() > dumpOrig.size());
    assertIterableEquals(dump.subList(0, dumpOrig.size()), dumpOrig);
  }

  @Test
  public void whenAppendingSameTimeAsPrevTime_shouldThrow() throws Exception {
    final UUID logId = copyTwoEventsToTempLog();
    final List<RowDumpEntry> dumpOrig = dumpAllRows(logId);

    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
      final EventTime eventTime2 = new EventTime(Instant.parse("1111-11-11T00:00:00Z"), 1);
      final EventEnvelope event2 =
          new EventEnvelope(
              eventTime2, UUID.randomUUID(), "pojo:io.ulzha.spive.test.InceptProcess", "\"EIEIO\"");
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> eventLog.appendIfPrevTimeMatch(event2, eventTime2));
    }

    final List<RowDumpEntry> dump = dumpAllRows(logId);
    assertThat(dump, is(dumpOrig));
  }

  @Test
  public void givenClosedLog_whenAppendingOneMore_shouldThrow() throws Exception {
    final UUID logId = copyOneEventClosedToTempLog();
    final List<RowDumpEntry> dumpOrig = dumpAllRows(logId);

    try (BigtableEventLog eventLog = new BigtableEventLog(testDataClient, logId)) {
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

    final List<RowDumpEntry> dump = dumpAllRows(logId);
    assertThat(dump, is(dumpOrig));
  }

  private EventEnvelope dummyEvent(int i) {
    return new EventEnvelope(
        new EventTime(Instant.parse("1111-11-11T00:00:00Z"), i),
        null,
        "pojo:io.ulzha.spive.test.WhamProcess",
        "\"WHAM!\"");
  }

  private record RowDumpEntry(List<RowCell> cells, List<String> values) {
    private static RowDumpEntry fromRow(Row row) {
      return new RowDumpEntry(
          row.getCells(),
          row.getCells().stream().map(cell -> cell.getValue().toStringUtf8()).toList());
    }
  }

  private List<RowDumpEntry> dumpAllRows(UUID logId) {
    final List<RowDumpEntry> dump = new ArrayList<>();

    var stream =
        testDataClient.readRows(
            Query.create("event-store")
                .prefix(logId + ":")
                .filter(
                    FILTERS
                        .chain()
                        .filter(FILTERS.limit().cellsPerColumn(1))
                        .filter(FILTERS.family().exactMatch("event"))));
    for (Row row : stream) {
      dump.add(RowDumpEntry.fromRow(row));
    }

    return dump;
  }

  private UUID copyTwoEventsToTempLog() {
    final UUID logId = UUID.randomUUID();

    EventTime prevTime = EventTime.INFINITE_PAST;

    String rowKey = logId + ":" + prevTime.toOrderPreservingString();
    RowMutation m =
        RowMutation.create("event-store", rowKey)
            .setCell(
                "event",
                "metadata",
                "{\"time\": \"1111-11-11T00:00:00Z#0\", \"type\": \"pojo:io.ulzha.spive.test.CreateProcess\"}")
            .setCell(
                "event",
                "payload",
                "{\"processId\": \"00000000-0000-0000-0000-000000000000\", \"name\": \"Yuck\", \"version\": \"0.0.1\", \"artifact\": \"file:///app/target/yuck-0.0.1-SNAPSHOT.jar;mainClass=com.yuck.app.spive.gen.YuckInstance$Main\", \"availabilityZones\": [\"dev-0\"], \"inputStreamIds\": [\"11111111-1111-1111-1111-111111111111\"], \"outputStreamIds\": [\"11111111-1111-1111-1111-111111111111\"]}");
    testDataClient.mutateRow(m);
    prevTime = EventTime.fromString("1111-11-11T00:00:00Z#0");

    rowKey = logId + ":" + prevTime.toOrderPreservingString();
    m =
        RowMutation.create("event-store", rowKey)
            .setCell(
                "event",
                "metadata",
                "{\"time\": \"1111-11-11T00:00:00Z#1\", \"type\": \"pojo:io.ulzha.spive.test.DeleteProcess\"}")
            .setCell(
                "event", "payload", "{\"processId\": \"00000000-0000-0000-0000-000000000000\"}");
    testDataClient.mutateRow(m);

    return logId;
  }

  private UUID copyOneEventClosedToTempLog() {
    final UUID logId = UUID.randomUUID();

    EventTime prevTime = EventTime.INFINITE_PAST;

    String rowKey = logId + ":" + prevTime.toOrderPreservingString();
    RowMutation m =
        RowMutation.create("event-store", rowKey)
            .setCell(
                "event",
                "metadata",
                "{\"time\": \"1111-11-11T00:00:00Z#0\", \"type\": \"pojo:io.ulzha.spive.test.CreateProcess\"}")
            .setCell(
                "event",
                "payload",
                "{\"processId\": \"00000000-0000-0000-0000-000000000000\", \"name\": \"Yuck\", \"version\": \"0.0.1\", \"artifact\": \"file:///app/target/yuck-0.0.1-SNAPSHOT.jar;mainClass=com.yuck.app.spive.gen.YuckInstance$Main\", \"availabilityZones\": [\"dev-0\"], \"inputStreamIds\": [\"11111111-1111-1111-1111-111111111111\"], \"outputStreamIds\": [\"11111111-1111-1111-1111-111111111111\"]}");
    testDataClient.mutateRow(m);
    prevTime = EventTime.fromString("1111-11-11T00:00:00Z#0");

    rowKey = logId + ":" + prevTime.toOrderPreservingString();
    m = RowMutation.create("event-store", rowKey).setCell("event", "metadata", "");
    testDataClient.mutateRow(m);

    return logId;
  }

  private UUID emptyTempLog() {
    return UUID.randomUUID();
  }
}
