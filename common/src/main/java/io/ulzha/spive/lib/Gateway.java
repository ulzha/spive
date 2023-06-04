package io.ulzha.spive.lib;

import io.ulzha.spive.lib.umbilical.UmbilicalWriter;

/**
 * All methods must report their exceptions before rethrowing - so we don't solely rely on user code
 * for their propagation.
 */
public abstract class Gateway {
  protected final UmbilicalWriter umbilicus;

  public Gateway(final UmbilicalWriter umbilicus) {
    this.umbilicus = umbilicus;
  }
}
