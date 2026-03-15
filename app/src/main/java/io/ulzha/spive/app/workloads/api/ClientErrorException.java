package io.ulzha.spive.app.workloads.api;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;

/**
 * Convenience wrapper to avoid boilerplate of crafting and propagating an HttpResponse in every
 * unhappy path of every API handler.
 */
public class ClientErrorException extends RuntimeException {
  static final long serialVersionUID = 42L;

  private final int code;

  public ClientErrorException(HttpStatus status, String message, Throwable cause) {
    // Kind of unclear if it is safe to communicate cause's message to client, so we won't.
    // We will expand the oneliner we log, though.
    super(message, cause);
    this.code = status.code();
  }

  public ClientErrorException(HttpStatus status, String message) {
    super(message);
    this.code = status.code();
  }

  public HttpResponse toHttpResponse() {
    return HttpResponse.of(HttpStatus.valueOf(code), MediaType.PLAIN_TEXT, getMessage());
  }

  @Override
  public Throwable fillInStackTrace() {
    // Maybe should learn about Flags.verboseExceptionSampler and the associated features...
    // For now, just override in such a way that client errors never log their stacktrace.
    return this;
  }
}
