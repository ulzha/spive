package io.ulzha.spive.app.workloads.watchdog;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import io.ulzha.spive.app.events.InstanceProgress;
import io.ulzha.spive.app.events.InstanceStatusChange;
import io.ulzha.spive.app.model.InstanceStatus;
import io.ulzha.spive.app.model.Process;
import io.ulzha.spive.app.spive.gen.SpiveOutputGateway;
import io.ulzha.spive.lib.EventIterator;
import io.ulzha.spive.lib.EventLock;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.InMemoryEventLog;
import io.ulzha.spive.lib.umbilical.HeartbeatSnapshot;
import io.ulzha.spive.lib.umbilical.ProgressUpdate;
import io.ulzha.spive.lib.umbilical.ProgressUpdatesList;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PollLoopTest {
  private Process process;
  private Process.Instance instance;

  private FakePlacenta fakePlacenta;
  private AtomicReference<Instant> controlPlaneWallClockTime;
  private InMemoryEventLog eventLog;
  private PollLoop pollLoop;

  @BeforeEach
  void setUp() {
    // annoyingly hairy. Should create a cleaner harness
    process =
        new Process(
            "foo",
            "0.0.1",
            UUID.fromString("3-2-3-2-3"),
            "foo-artifact",
            List.of(),
            Set.of(),
            Set.of());
    instance =
        new Process.Instance(UUID.fromString("1-2-3-4-5"), process, URI.create("http://foo"));

    instance.timeoutMillis = 15000;
    instance.checkpoint = EventTime.INFINITE_PAST;
    instance.status = InstanceStatus.NOMINAL;

    fakePlacenta = new FakePlacenta();
    final UmbilicalWriter fakeUmbilicus = new FakeUmbilicus();
    controlPlaneWallClockTime = new AtomicReference<>(Instant.EPOCH);
    eventLog = new InMemoryEventLog();
    final EventIterator eventIterator = new EventIterator(eventLog.iterator());
    final Supplier<Instant> wallClockAutoAdvancingMimickingReads =
        () -> {
          // not sure we should be _mimicking_ reads. Realistic event history and real reads FTW?
          // Fake output gateway entirely when unittesting a sporadic workload
          eventIterator.lastTimeRead = eventLog.latestEventTime();
          if (controlPlaneWallClockTime.get().compareTo(eventLog.latestEventTime().instant) <= 0) {
            controlPlaneWallClockTime.set(eventLog.latestEventTime().instant.plusMillis(1));
          }
          return controlPlaneWallClockTime.get();
        };
    final EventLock eventLock = new EventLock();
    final SpiveOutputGateway fakeOutput =
        new SpiveOutputGateway(
            fakeUmbilicus, eventIterator, wallClockAutoAdvancingMimickingReads, eventLock);

    pollLoop =
        new PollLoop(instance, fakePlacenta, () -> controlPlaneWallClockTime.get(), fakeOutput);
  }

  @Test
  void whenFirstSuccessfullyHandledEventInHeartbeat_shouldEmitInstanceProgress()
      throws InterruptedException {
    final EventTime t1 = EventTime.fromString("2021-04-14T10:00:01Z#0");
    final var sample =
        List.of(
            new ProgressUpdatesList(
                t1,
                null,
                List.of(
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:01.111Z"), false, null, null),
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:01.222Z"), true, null, null))));
    fakePlacenta.givenHeartbeatSnapshot(new HeartbeatSnapshot(sample, t1, 1, 0));

    pollLoop.pollOnce();

    final List<Object> expected = List.of(new InstanceProgress(instance.id, t1, 1, 0));
    assertIterableEquals(expected, eventLog.asPayloadList());
  }

  @Test
  void whenNoNewSuccessfullyHandledEventInHeartbeat_shouldNotEmitInstanceProgress()
      throws InterruptedException {
    final EventTime t1 = EventTime.fromString("2021-04-14T10:00:01Z#0");
    final EventTime t2 = EventTime.fromString("2021-04-14T10:00:02Z#0");
    final var sample =
        List.of(
            new ProgressUpdatesList(
                t2,
                null,
                List.of(
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:02.111Z"), false, null, null))));
    fakePlacenta.givenHeartbeatSnapshot(new HeartbeatSnapshot(sample, t1, 1, 0));

    instance.checkpoint = t1;
    pollLoop.pollOnce();

    final List<Object> expected = List.of();
    assertIterableEquals(expected, eventLog.asPayloadList());
  }

  @Test
  void whenNewSuccessfullyHandledEventInHeartbeat_shouldEmitInstanceProgress()
      throws InterruptedException {
    final EventTime t1 = EventTime.fromString("2021-04-14T10:00:01Z#0");
    final EventTime t2 = EventTime.fromString("2021-04-14T10:00:02Z#0");
    final var sample =
        List.of(
            new ProgressUpdatesList(
                t2,
                null,
                List.of(
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:02.111Z"), false, null, null),
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:02.111Z"), true, null, null))));
    fakePlacenta.givenHeartbeatSnapshot(new HeartbeatSnapshot(sample, t2, 2, 0));

    instance.checkpoint = t1;
    pollLoop.pollOnce();

    final List<Object> expected = List.of(new InstanceProgress(instance.id, t2, 2, 0));
    assertIterableEquals(expected, eventLog.asPayloadList());
  }

  @Test
  void givenDeletedInstance_shouldNotEmitInstanceProgress() throws InterruptedException {
    final EventTime t1 = EventTime.fromString("2021-04-14T10:00:01Z#0");
    final var sample =
        List.of(
            new ProgressUpdatesList(
                t1,
                null,
                List.of(
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:01.111Z"), false, null, null),
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:01.222Z"), true, null, null))));
    fakePlacenta.givenHeartbeatSnapshot(new HeartbeatSnapshot(sample, t1, 1, 0));

    instance.process = null;
    pollLoop.pollOnce();

    final List<Object> expected = List.of();
    assertIterableEquals(expected, eventLog.asPayloadList());
  }

  @Test
  void givenNominalStatus_whenTimeoutInHeartbeat_shouldDetectTimeoutStatus()
      throws InterruptedException {
    final EventTime t1 = EventTime.fromString("2021-04-14T10:00:01Z#0");
    final var sample =
        List.of(
            new ProgressUpdatesList(
                t1,
                null,
                List.of(
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:01.111Z"), false, null, null))));
    fakePlacenta.givenHeartbeatSnapshot(new HeartbeatSnapshot(sample, null, 0, 0));

    controlPlaneWallClockTime.set(Instant.parse("2021-04-14T10:55:00Z"));
    pollLoop.pollOnce();

    final List<Object> expected =
        List.of(
            new InstanceStatusChange(
                instance.id,
                t1,
                Instant.parse("2021-04-14T10:00:16.111Z"),
                InstanceStatus.TIMEOUT.name(),
                null));
    assertIterableEquals(expected, eventLog.asPayloadList());
  }

  @Test
  void givenErrorStatus_whenNoErrorInHeartbeat_shouldNotEmitInstanceStatusChange()
      throws InterruptedException {
    // Runners must keep reporting the first error indefinitely.
    // This test is paranoid to survive a buggy runner that reports non-error heartbeat anyway.
    final EventTime t1 = EventTime.fromString("2021-04-14T10:00:01Z#0");
    final EventTime t2 = EventTime.fromString("2021-04-14T10:00:02Z#0");
    final var sample =
        List.of(
            new ProgressUpdatesList(
                t2,
                null,
                List.of(
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:02.111Z"), false, null, null))));
    fakePlacenta.givenHeartbeatSnapshot(new HeartbeatSnapshot(sample, t1, 1, 0));

    instance.checkpoint = t1;
    instance.status = InstanceStatus.ERROR;
    controlPlaneWallClockTime.set(Instant.parse("2021-04-14T10:00:02.222Z"));
    pollLoop.pollOnce();

    final List<Object> expected = List.of();
    assertIterableEquals(expected, eventLog.asPayloadList());

    // TODO assert that error is logged
  }

  //  @Test
  //  void givenExitStatus_whenNoExitInHeartbeat_shouldNotEmitInstanceStatus() {
  //    // Runners should keep reporting the exit indefinitely.
  //    // This test is paranoid to survive buggy runners.
  //
  //    // assert that error is logged
  //  }

  //  @Test
  //  void whenHeartbeatIsAbsent_shouldDetectLosingStatus() {
  //  }

  @Test
  void whenNoErrorOrTimeoutInHeartbeat_shouldDetectNominalStatus() throws InterruptedException {
    final EventTime t1 = EventTime.fromString("2021-04-14T10:00:01Z#0");
    final EventTime t2 = EventTime.fromString("2021-04-14T10:00:02Z#0");
    final var sample =
        List.of(
            new ProgressUpdatesList(
                t2,
                null,
                List.of(
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:02.111Z"), false, null, null))));
    fakePlacenta.givenHeartbeatSnapshot(new HeartbeatSnapshot(sample, t1, 1, 0));

    instance.checkpoint = t1;
    instance.status = InstanceStatus.TIMEOUT;
    controlPlaneWallClockTime.set(Instant.parse("2021-04-14T10:00:02.222Z"));
    pollLoop.pollOnce();

    final List<Object> expected =
        List.of(
            new InstanceStatusChange(instance.id, t2, null, InstanceStatus.NOMINAL.name(), null));
    assertIterableEquals(expected, eventLog.asPayloadList());
  }

  @Test
  void givenNominalStatus_whenErrorInHeartbeat_shouldDetectErrorStatus()
      throws InterruptedException {
    final EventTime t1 = EventTime.fromString("2021-04-14T10:00:01Z#0");
    final var sample =
        List.of(
            new ProgressUpdatesList(
                t1,
                null,
                List.of(
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:01.111Z"), false, null, "err"))));
    fakePlacenta.givenHeartbeatSnapshot(
        new HeartbeatSnapshot(sample, EventTime.INFINITE_PAST, 0, 0));

    controlPlaneWallClockTime.set(Instant.parse("2021-04-14T10:55:00Z"));
    pollLoop.pollOnce();

    final List<Object> expected =
        List.of(
            new InstanceStatusChange(
                instance.id,
                t1,
                Instant.parse("2021-04-14T10:00:01.111Z"),
                InstanceStatus.ERROR.name(),
                "err"));
    assertIterableEquals(expected, eventLog.asPayloadList());
  }

  @Test
  void givenTimeoutStatus_whenRecoveryInHeartbeat_shouldDetectNominalStatus()
      throws InterruptedException {
    final EventTime t1 = EventTime.fromString("2021-04-14T10:00:01Z#0");
    final var sample =
        List.of(
            new ProgressUpdatesList(
                t1,
                null,
                List.of(
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:01.111Z"), false, null, null),
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:54:59.999Z"), true, null, null))));
    fakePlacenta.givenHeartbeatSnapshot(new HeartbeatSnapshot(sample, t1, 1, 0));

    instance.status = InstanceStatus.TIMEOUT;
    pollLoop.pollOnce();

    final List<Object> expected =
        List.of(
            new InstanceProgress(instance.id, t1, 1, 0),
            new InstanceStatusChange(instance.id, t1, null, InstanceStatus.NOMINAL.name(), null));
    assertIterableEquals(expected, eventLog.asPayloadList());
  }

  @Test
  void givenTimeoutStatus_whenNoRecoveryInHeartbeat_shouldNotEmitInstanceStatusChange()
      throws InterruptedException {
    final EventTime t1 = EventTime.fromString("2021-04-14T10:00:01Z#0");
    final var sample =
        List.of(
            new ProgressUpdatesList(
                t1,
                null,
                List.of(
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:01.111Z"), false, null, null))));
    fakePlacenta.givenHeartbeatSnapshot(new HeartbeatSnapshot(sample, null, 0, 0));

    instance.status = InstanceStatus.TIMEOUT;
    controlPlaneWallClockTime.set(Instant.parse("2021-04-14T10:55:00Z"));
    pollLoop.pollOnce();

    final List<Object> expected = List.of();
    assertIterableEquals(expected, eventLog.asPayloadList());
  }

  @Test
  void givenDeletedInstance_shouldNotEmitInstanceStatusChange() throws InterruptedException {
    final EventTime t1 = EventTime.fromString("2021-04-14T10:00:01Z#0");
    final EventTime t2 = EventTime.fromString("2021-04-14T10:00:02Z#0");
    final var sample =
        List.of(
            new ProgressUpdatesList(
                t2,
                null,
                List.of(
                    new ProgressUpdate(
                        Instant.parse("2021-04-14T10:00:02.111Z"), false, null, null))));
    fakePlacenta.givenHeartbeatSnapshot(new HeartbeatSnapshot(sample, t1, 1, 0));

    instance.process = null;
    instance.status = InstanceStatus.TIMEOUT;
    controlPlaneWallClockTime.set(Instant.parse("2021-04-14T10:00:02.222Z"));
    pollLoop.pollOnce();

    final List<Object> expected = List.of();
    assertIterableEquals(expected, eventLog.asPayloadList());
  }
}
