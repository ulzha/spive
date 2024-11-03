package io.ulzha.spive.example.clicctracc.app;

import io.ulzha.spive.example.clicctracc.app.events.Clicc;
import io.ulzha.spive.example.clicctracc.app.spive.gen.CliccTraccInstance;
import io.ulzha.spive.example.clicctracc.app.spive.gen.CliccTraccOutputGateway;
import io.ulzha.spive.lib.EventTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simplistic scheduler process.
 *
 * <p>Emits an event every hour sharp, in a reliable fashion - catches up and does not skip over
 * hours even if temporary downtime in processing time occurs.
 *
 * <p>Can be used as a durable cron thingy.
 */
public class CliccTracc implements CliccTraccInstance {
  private static final Logger LOG = LoggerFactory.getLogger(CliccTracc.class);
  private final CliccTraccOutputGateway output;
  private volatile Instant lastClicc;

  public CliccTracc(CliccTraccOutputGateway output) {
    this.output = output;
  }

  public void accept(final Clicc event, final EventTime eventTime) {
    lastClicc = eventTime.instant;
  }

  public class Metronome implements Runnable {
    private Instant initClicc(Instant x) {
      return x.truncatedTo(ChronoUnit.HOURS);
    }

    private Instant nextClicc(Instant prevClicc) {
      return prevClicc.plus(1, ChronoUnit.HOURS);
    }

    private boolean isImpendingClicc(Instant tentative) {
      // tentative may be many hours ahead of the last event in the log - rule out such adversity
      return lastClicc == null || tentative == nextClicc(lastClicc);
    }

    private Instant cliccUntil(Instant end) {
      Instant tentative = (lastClicc == null ? initClicc(end) : nextClicc(lastClicc));
      while (tentative.isBefore(end)) {
        final EventTime cliccTime = new EventTime(tentative);
        LOG.info(
            "Emit "
                + cliccTime
                + ": "
                + output.emitIf(() -> isImpendingClicc(cliccTime.instant), new Clicc(), cliccTime));
        // NB we don't know how much closer we are now putting tentative to end
        // (it could be even backward in the first iteration, the first emitIf coming across
        // existing past events. Even if Metronome is only started after catching up with
        // preexisting events, on clean process start there's zero of them)
        tentative = nextClicc(lastClicc == null ? tentative : lastClicc);
      }
      return tentative;
    }

    @Override
    public void run() {
      try {
        while (true) {
          // TODO pluggable Clock and pluggable sleep - for testing and offset/scaled time. Can't
          // seem to google any off-the-shelf "emulated time" library... emulated.Thread.sleep
          // collaboration may be affected by emitting eventTimes arbitrarily far removed from any
          // predictable clock
          Instant now = Instant.now();
          LOG.info("Cliccing up to: " + now);
          Instant impending = cliccUntil(now);
          long sleepMs = impending.toEpochMilli() - now.toEpochMilli();
          LOG.info("Sleeping for: " + sleepMs);
          Thread.sleep(sleepMs);
          // NB we don't know how long we've slept
          // (we generally don't know how much slowdown/hanging a given replica experiences, wrt
          // healthy ones that may be concurrently cliccing forward)
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
