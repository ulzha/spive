package io.ulzha.spive.lib;

/**
 * Decorator around problems that are propagated through various Spīve layers. Geared towards
 * prevention of confusing duplicate messaging.
 *
 * <p>Not for use outside io.ulzha.spive
 */
public class HandledException extends RuntimeException {
  static final long serialVersionUID = 42L;

  public HandledException(Throwable cause) {
    super(cause);
  }
}
