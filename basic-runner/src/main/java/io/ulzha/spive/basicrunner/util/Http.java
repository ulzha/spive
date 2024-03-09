package io.ulzha.spive.basicrunner.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.Nullable;
import jakarta.json.bind.Jsonb;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience instrumentation around HttpServer offering sensible defaults + serde and typing
 * helpers.
 */
public class Http {
  private static final Logger LOG = LoggerFactory.getLogger(Http.class);
  private static final Jsonb JSONB = Json.create();

  private enum ExchangeAttribute {
    HTTP_START_TIME,
  }

  /** HttpServer itself doesn't log basic things such as exceptions and timings */
  private static HttpHandler rootHandler(HttpHandler delegate) {
    return (exchange) -> {
      RuntimeException e = null;

      exchange.setAttribute(
          ExchangeAttribute.HTTP_START_TIME.name(), Long.valueOf(System.currentTimeMillis()));
      try {
        delegate.handle(exchange);
        if (exchange.getResponseCode() == -1) {
          throw new IllegalStateException(
              "No HTTP status code sent despite normal return - make sure the exchange is thoroughly handled");
        }
      } catch (Throwable t) {
        e = new RuntimeException("Error handling HTTP exchange", t);
        int prevCode = exchange.getResponseCode();
        if (prevCode == -1) {
          try {
            exchange.sendResponseHeaders(StatusCode.INTERNAL_SERVER_ERROR.value, -1);
          } catch (Throwable t2) {
            e.addSuppressed(new RuntimeException("Failed to send response headers", t2));
          }
        } else {
          e.addSuppressed(
              new RuntimeException(
                  "Exception after HTTP status code %d was already sent - make sure body serialization and return from handlers is robust"
                      .formatted(prevCode)));
        }
      }

      try {
        final Long startTime =
            (Long) exchange.getAttribute(ExchangeAttribute.HTTP_START_TIME.name());
        final double duration = .001 * (System.currentTimeMillis() - startTime.longValue());
        LOG.info(
            "{} {} {} {}",
            exchange.getRequestMethod(),
            exchange.getRequestURI(),
            exchange.getResponseCode(),
            duration);
        // dunno if this can be slow too
        exchange.close();
      } catch (Throwable t) {
        final RuntimeException eLate = new RuntimeException("Error closing HTTP exchange", t);
        if (e != null) {
          e.addSuppressed(eLate);
        } else {
          e = eLate;
        }
      }

      if (e != null) {
        LOG.error("Internal server error", e);
        throw e;
      }
    };
  }

  public static void startServer(int port, HttpHandler handler) {
    final HttpServer server;
    try {
      server = HttpServer.create(new InetSocketAddress(port), 1000, "/", rootHandler(handler));
      server.setExecutor(Executors.newFixedThreadPool(10));
      server.start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * https://stackoverflow.com/questions/730283/does-java-have-a-complete-enum-for-http-response-codes
   * it does not
   */
  public enum StatusCode {
    OK(200),
    ACCEPTED(201),
    NO_CONTENT(204),
    BAD_REQUEST(400),
    NOT_FOUND(404),
    CONFLICT(409),
    INTERNAL_SERVER_ERROR(500),
    NOT_IMPLEMENTED(501);

    public final int value;

    StatusCode(int value) {
      this.value = value;
    }
  }

  public static record Response<RespT>(StatusCode statusCode, @Nullable RespT body) {}

  public static <RespT> Response<RespT> response(StatusCode statusCode, RespT body) {
    return new Response<RespT>(statusCode, body);
  }

  public static <RespT> Response<RespT> response(StatusCode statusCode) {
    return new Response<RespT>(statusCode, null);
  }

  /** Handler that deserializes request body and serializes response. */
  private record JsonBiHandler<ReqT, RespT>(
      BiFunction<HttpExchange, ReqT, Response<RespT>> delegate,
      Class<ReqT> reqClass,
      Class<RespT> respClass)
      implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      try (InputStream is = exchange.getRequestBody()) {
        final ReqT req = JSONB.fromJson(is, reqClass);
        final Response<RespT> response = delegate.apply(exchange, req);
        emit(exchange, response, respClass);
      }
    }
  }

  /** Handler that ignores request body and serializes response. (Is for GET) */
  private record JsonHandler<RespT>(
      Function<HttpExchange, Response<RespT>> delegate, Class<RespT> respClass)
      implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      final Response<RespT> response = delegate.apply(exchange);
      emit(exchange, response, respClass);
    }
  }

  private static void emit(
      HttpExchange exchange, final Response<?> response, final Class<?> respClass)
      throws IOException {
    if (response.body == null) {
      // -1 means zero length, 0 means unknown length
      exchange.sendResponseHeaders(response.statusCode.value, -1);
    } else {
      exchange.sendResponseHeaders(response.statusCode.value, 0);
      // We fancy to stream our output without buffering it all, but feedback on eventual JSON
      // serialization failing will be unnice, given that a successful response code may have
      // already been sent.
      try (OutputStream os = exchange.getResponseBody()) {
        JSONB.toJson(response.body, respClass, os);
      }
    }
  }

  public static <ReqT, RespT> HttpHandler jsonHandler(
      BiFunction<HttpExchange, ReqT, Response<RespT>> handler,
      Class<ReqT> reqClass,
      Class<RespT> respClass) {
    return new JsonBiHandler<ReqT, RespT>(handler, reqClass, respClass);
  }

  public static <RespT> HttpHandler jsonHandler(
      Function<HttpExchange, Response<RespT>> handler, Class<RespT> respClass) {
    return new JsonHandler<RespT>(handler, respClass);
  }

  public static HttpHandler fourOhFourHandler() {
    return (exchange) -> {
      exchange.sendResponseHeaders(StatusCode.NOT_FOUND.value, -1);
    };
  }
}
