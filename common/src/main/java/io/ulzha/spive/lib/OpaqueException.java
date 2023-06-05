package io.ulzha.spive.lib;

/**
 * Decorator around problems that are propagated through various Spīve layers. Geared for
 * multitenancy support, indicates that the cause (full stacktrace) is not necessarily safe to
 * disclose to all users.
 *
 * <p>Not for use outside io.ulzha.spive
 */
public class OpaqueException extends HandledException {
  static final long serialVersionUID = 42L;

  public OpaqueException(Throwable cause) {
    super(cause);
  }
}
