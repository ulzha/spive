package io.ulzha.spive.lib;

/**
 * Intended to signal problems that are not supposed to occur by design - scenarios of the "should
 * not happen" kind, symptomatic of Spīve bugs.
 *
 * <p>As such the exception should always propagate to alert the maintainers of the platform.
 *
 * <p>Always explicitly highlight in message, what is the essential component/design invariant that
 * is supposed to safeguard against the particular problem, to the best of your understanding.
 */
// TODO more classes to point out non-core (contributed to platform) origins, as well as conditions
// when storage corruption is a likely factor rather than bugs
public class InternalException extends RuntimeException {
  static final long serialVersionUID = 42L;

  public InternalException(String message) {
    super(message);
  }

  public InternalException(String message, Throwable cause) {
    super(message, cause);
  }
}
