import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CliccTraccTest {

  @BeforeEach
  void setUp() {
    // final CliccTraccOutputGateway fakeOutput =
    // new CliccTraccOutputGateway(
    //     fakeUmbilicus,
    //     controlPlaneLastEventTime,
    //     wallClockAutoAdvancingMimickingReads,
    //     lockableEventLog);
    // CliccTracc cliccTracc = new CliccTracc(fakeOutput);
  }

  @Test
  void givenNoEvents_whenRunning_shouldEmitConsecutiveEventsEveryWallClockHourSharp()
      throws InterruptedException {
    // assert no event when small advances
  }

  @Test
  void givenUpToDateEvents_whenRunning_shouldKeepEmittingConsecutiveEventsEveryWallClockHourSharp()
      throws InterruptedException {
    // assert no event when small advances
  }

  @Test
  void givenEventsOlderThanAnHour_whenStarted_shouldEmitConsecutiveEventsUpToWallClockHour()
      throws InterruptedException {
    // assert events are emitted already before wall clock advances
  }

  @Test
  void
      givenUpToDateEvents_whenSuspendedAndUnsuspended_shouldEmitConsecutiveEventsUpToWallClockHour()
          throws InterruptedException {
    // assert plenty events when starved and finally wall clock advances
  }

  // TODO same tests but concurrent
  // "hard mode" let test framework enumerate permutations to certain depth
  // "easy mode" let programmers adjust sequence diagram-style what will exercise all/the most
  // important branches
}
