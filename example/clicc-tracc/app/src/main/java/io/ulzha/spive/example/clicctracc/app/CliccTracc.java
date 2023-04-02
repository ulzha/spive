package io.ulzha.spive.example.clicctracc.app;

import io.ulzha.spive.example.clicctracc.app.events.Clicc;
import io.ulzha.spive.example.clicctracc.app.lib.CliccTraccInstance;
import io.ulzha.spive.example.clicctracc.app.lib.CliccTraccOutputGateway;
import io.ulzha.spive.lib.EventTime;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * A simplistic scheduler process.
 *
 * <p>Emits an event every hour sharp, in a durable fashion - does not skip over hours even if
 * temporary downtime in processing time occurs.
 *
 * <p>Can be used as a durable cron thingy.
 */
public class CliccTracc implements CliccTraccInstance {
  private final CliccTraccOutputGateway output;
  private Instant lastClicc;

  public CliccTracc(CliccTraccOutputGateway output) {
    this.output = output;
  }

  public void accept(final Clicc event, final EventTime eventTime) {
    lastClicc = eventTime.instant;
  }

  public class Trigger implements Runnable {
    // ephemeral state in workloads is ok, so long as we are ok with it getting emptied on redeploy
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
              new EventTime(nextClicc(lastClicc, start)))) ;
    }

    @Override
    public void run() {
      try {
        start = Instant.now();
        while (true) {
          // TODO pluggable Clock - for testing and offset/scaled time
          // collaboration may be affected by emitting eventTimes arbitrarily far removed from any predictable clock
          Instant now = Instant.now();
          Instant impending = now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS);
          clicc(impending);
          long sleepMs = impending.toEpochMilli() - now.toEpochMilli();
          Thread.sleep(sleepMs);
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
