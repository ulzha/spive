package io.ulzha.spive.basicrunner.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jakarta.json.bind.Jsonb;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Convenience instrumentation around HttpServer offering sensible defaults, serde and typing
 * helpers.
 */
public class Http {
  private static final Jsonb JSONB = Json.create();
  private static final String STATUS_CODE = "HTTP_STATUS_CODE";

  public static void startServer(int port, HttpHandler handler) {
    HttpServer server;
    try {
      server = HttpServer.create(new InetSocketAddress(port), 1000, "/", handler);
      server.setExecutor(Executors.newFixedThreadPool(10));
      server.start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Quick drop-in replacements for java.util counterparts, to mitigate checked exceptions noise.
   */
  @FunctionalInterface
  public interface BiFunction<T, U, R> {
    R apply(T t, U u) throws IOException;
  }

  @FunctionalInterface
  public interface Function<T, R> {
    R apply(T t) throws IOException;
  }

  /** Handler that deserializes request body and serializes response. */
  private record JsonBiHandler<ReqT, RespT>(
      BiFunction<HttpExchange, ReqT, RespT> delegate, Class<ReqT> reqClass, Class<RespT> respClass)
      implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      try (InputStream is = exchange.getRequestBody()) {
        final ReqT req = JSONB.fromJson(is, reqClass);
        final RespT resp = delegate.apply(exchange, req);
        // We fancy to stream our output without buffering it all, but feedback on eventual JSON
        // serialization failing will be unnice, given that a successful response code may have
        // already been sent.
        try (OutputStream os = exchange.getResponseBody()) {
          JSONB.toJson(resp, respClass, os);
        }
      } finally {
        exchange.close();
      }
    }
  }

  /** Handler that ignores request body and serializes response. (Is for GET) */
  private record JsonHandler<RespT>(Function<HttpExchange, RespT> delegate, Class<RespT> respClass)
      implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      try {
        final RespT resp = delegate.apply(exchange);
        // We fancy to stream our output without buffering it all, but feedback on eventual JSON
        // serialization failing will be unnice, given that a successful response code may have
        // already been sent.
        try (OutputStream os = exchange.getResponseBody()) {
          JSONB.toJson(resp, respClass, os);
        }
      } finally {
        exchange.close();
      }
    }
  }

  public static <ReqT, RespT> HttpHandler jsonHandler(
      BiFunction<HttpExchange, ReqT, RespT> handler, Class<ReqT> reqClass, Class<RespT> respClass) {
    return new JsonBiHandler<ReqT, RespT>(handler, reqClass, respClass);
  }

  public static <RespT> HttpHandler jsonHandler(
      Function<HttpExchange, RespT> handler, Class<RespT> respClass) {
    return new JsonHandler<RespT>(handler, respClass);
  }
}
