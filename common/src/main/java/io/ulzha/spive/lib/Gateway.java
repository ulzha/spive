package io.ulzha.spive.lib;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;

/**
 * All methods must report their exceptions before rethrowing - so we don't solely rely on user code
 * for their propagation.
 */
public abstract class Gateway {
  // used only by subclasses directly so far
  @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
  protected final UmbilicalWriter umbilicus;

  public Gateway(final UmbilicalWriter umbilicus) {
    this.umbilicus = umbilicus;
  }
}
