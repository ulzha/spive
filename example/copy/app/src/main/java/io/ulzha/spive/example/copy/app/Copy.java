package io.ulzha.spive.example.copy.app;

import io.ulzha.spive.example.copy.app.events.CreateFoo;
import io.ulzha.spive.example.copy.app.lib.CopyInstance; // TODO a toy event, perhaps
import io.ulzha.spive.example.copy.app.lib.CopyOutputGateway;

/**
 * A trivial process that copies events from one Stream to another. The input and output streams are
 * to be configured through UI.
 */
public class Copy implements CopyInstance {
  private final CopyOutputGateway output;

  public Copy(CopyOutputGateway output) {
    this.output = output;
  }

  public void accept(final CreateFoo event) {
    output.emitConsequential(event);
  }
}
