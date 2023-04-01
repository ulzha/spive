package io.ulzha.spive.example.clicctracc.app;

import io.ulzha.spive.example.clicctracc.app.events.Clicc;
import io.ulzha.spive.example.clicctracc.app.lib.CliccTraccInstance;
import io.ulzha.spive.example.clicctracc.app.lib.CliccTraccOutputGateway;

/**
 * A quite trivial process that emits an event every hour sharp, in a durable fashion - does not
 * skip any hours even if temporary downtime in processing time occurs.
 *
 * The output stream is to be configured through UI.
 */
public class CliccTracc implements CliccTraccInstance {
  private final CliccTraccOutputGateway output;
  private Instant lastClicc;

  public CliccTracc(ClicTraccOutputGateway output) {
    this.output = output;
  }

  public void accept(final Clicc event) {
    lastClicc = event.time;
  }
}
