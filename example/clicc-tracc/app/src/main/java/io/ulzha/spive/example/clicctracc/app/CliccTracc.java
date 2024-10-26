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
  private Instant lastClicc;

  public CliccTracc(CliccTraccOutputGateway output) {
    this.output = output;
  }

  public void accept(final Clicc event, final EventTime eventTime) {
    lastClicc = eventTime.instant;
  }

  public class Metronome implements Runnable {
    // ephemeral state in workloads is ok, so long as we are ok with it getting emptied on redeploy
    // TODO must clicc the first one ahead of time, to count as durable against adversary redeploy?
    // Or a special "started" event?
    private Instant start;

    private Instant nextClicc(Instant lastClicc, Instant start) {
      if (lastClicc == null) {
        return start.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS);
      } else {
        return lastClicc.plus(1, ChronoUnit.HOURS);
      }
    }

    private void clicc(Instant end) {
      while (output.emitIf(
          () -> nextClicc(lastClicc, start).isBefore(end),
          new Clicc(),
          new EventTime(nextClicc(lastClicc, start))))
        ;
    }

    @Override
    public void run() {
      try {
        start = Instant.now();
        while (true) {
          // TODO pluggable Clock and pluggable sleep - for testing and offset/scaled time. Can't
          // seem to google any off-the-shelf "emulated time" library... emulated.Thread.sleep
          // collaboration may be affected by emitting eventTimes arbitrarily far removed from any
          // predictable clock
          Instant now = Instant.now();
          Instant impending = now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS);
          LOG.info("Cliccing: " + impending);
          clicc(impending);
          long sleepMs = impending.toEpochMilli() - now.toEpochMilli();
          LOG.info("Sleeping for: " + sleepMs);
          Thread.sleep(sleepMs);
        }
      } catch (Exception e) {
        LOG.error("Definitely not here?", e);
        throw new RuntimeException(e);
      }
    }
  }
}
