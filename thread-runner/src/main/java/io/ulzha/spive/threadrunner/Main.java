package io.ulzha.spive.threadrunner;

import com.spotify.apollo.Environment;
import com.spotify.apollo.Response;
import com.spotify.apollo.httpservice.HttpService;
import com.spotify.apollo.route.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Application entry point. */
public final class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  private Main() {}

  /**
   * Runs the application.
   *
   * @param args command-line arguments
   */
  public static void main(final String... args) throws Exception {
    HttpService.boot(Main::init, "spive-thread-runner", args);
  }

  static void init(final Environment environment) {
    environment
        .routingEngine()
        .registerRoute(Route.sync("GET", "/v0/ping", (context) -> Response.ok()));
    environment.routingEngine().registerRoutes(new ThreadRunnerResource().routes());
    // TODO log latest heartbeats as warnings in closer if they have not been polled
  }
}
