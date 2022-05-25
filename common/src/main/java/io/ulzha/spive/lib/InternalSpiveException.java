package io.ulzha.spive.lib;

/**
 * Intended to signal problems that are not supposed to occur by design - scenarios of the "should
 * not happen" kind, symptomatic of Spīve bugs.
 *
 * <p>As such the exception should always propagate to alert the maintainers of the platform.
 */
public class InternalSpiveException extends RuntimeException {
  static final long serialVersionUID = 42L;

  public InternalSpiveException(String message) {
    super(message);
  }

  public InternalSpiveException(String message, Throwable cause) {
    super(message, cause);
  }
}
