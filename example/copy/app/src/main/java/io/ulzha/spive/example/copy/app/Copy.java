package io.ulzha.spive.example.copy.app;

// TODO dunno if a toy event of specific type, or perhaps a generics mechanism is worthwhile
import io.ulzha.spive.example.copy.app.events.CreateFoo;
import io.ulzha.spive.example.copy.app.lib.CopyInstance;
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
