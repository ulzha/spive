package io.ulzha.spive.basicrunner.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.ulzha.spive.lib.InternalException;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Convenience HttpHandler with routing and parameter parsing helpers. */
public class Rest {
  private static final Logger LOG = LoggerFactory.getLogger(Rest.class);

  private enum ExchangeAttribute {
    REST_PATH_PARAMS,
  }

  public record Route(Pattern methodRegex, Pattern pathRegex, HttpHandler handler) {}

  public static HttpHandler handler(HttpHandler fallbackHandler, Route... routes) {
    // could be done more composably (?) with filters. Not craving the bloat for now though:
    // ClosingFilter, LoggingFilter, RoutingFilter, ParsingFilter...
    return (exchange) -> {
      // try {
      final String method = exchange.getRequestMethod();
      final String path = exchange.getRequestURI().getPath();
      for (Route route : routes) {
        final Matcher methodMatcher = route.methodRegex().matcher(method);
        final Matcher pathMatcher = route.pathRegex().matcher(path);
        if (methodMatcher.matches() && pathMatcher.matches()) {
          LOG.info(
              "{} {} matched route {}",
              exchange.getRequestMethod(),
              exchange.getRequestURI(),
              route.pathRegex());
          setPathParams(exchange, pathMatcher);
          route.handler().handle(exchange);
          return;
        }
      }
      LOG.info("{} {} matched no route", exchange.getRequestMethod(), exchange.getRequestURI());
      fallbackHandler.handle(exchange);
    };
  }

  public static Route route(String methodPattern, String pathPattern, HttpHandler handler) {
    return new Route(Pattern.compile(methodPattern), Pattern.compile(pathPattern), handler);
  }

  public static Route delete(String pathPattern, HttpHandler handler) {
    return route("^DELETE$", pathPattern, handler);
  }

  public static Route get(String pathPattern, HttpHandler handler) {
    return route("^GET$", pathPattern, handler);
  }

  public static Route patch(String pathPattern, HttpHandler handler) {
    return route("^PATCH$", pathPattern, handler);
  }

  public static Route post(String pathPattern, HttpHandler handler) {
    return route("^POST$", pathPattern, handler);
  }

  public static Route put(String pathPattern, HttpHandler handler) {
    return route("^PUT$", pathPattern, handler);
  }

  @SuppressWarnings("unchecked")
  private static void setPathParams(HttpExchange exchange, Matcher pathMatcher) {
    final Map<String, Integer> namedGroups;
    // HACK - FIXME use pathMatcher.namedGroups() of Java 20. (Obsolete --add-opens then, too)
    // https://bugs.openjdk.java.net/browse/JDK-7032377
    try {
      // https://stackoverflow.com/questions/15588903/get-group-names-in-java-regex/76399200#76399200
      final Field namedGroupsField =
          pathMatcher.pattern().getClass().getDeclaredField("namedGroups");
      namedGroupsField.setAccessible(true);
      namedGroups = (Map<String, Integer>) namedGroupsField.get(pathMatcher.pattern());
    } catch (Exception e) {
      throw new InternalException(
          "Could not access named capturing groups of a Pattern - our non-public API hack may need fixing",
          e);
    }

    final Map<String, String> pathParams = new HashMap<>();
    if (namedGroups != null) {
      for (var entry : namedGroups.entrySet()) {
        pathParams.put(
            entry.getKey(),
            URLDecoder.decode(pathMatcher.group(entry.getValue()), StandardCharsets.UTF_8));
      }
    }
    exchange.setAttribute(ExchangeAttribute.REST_PATH_PARAMS.name(), pathParams);
  }

  @SuppressWarnings("unchecked")
  public static String pathParam(HttpExchange exchange, String name) {
    final Map<String, String> pathParams =
        (Map<String, String>) exchange.getAttribute(ExchangeAttribute.REST_PATH_PARAMS.name());
    return pathParams.get(name);
  }
}
