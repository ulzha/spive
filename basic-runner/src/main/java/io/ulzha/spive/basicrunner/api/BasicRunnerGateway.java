package io.ulzha.spive.basicrunner.api;

import io.ulzha.spive.lib.Gateway;
import io.ulzha.spive.lib.InternalException;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses spive-basic-runner, a minimalistic processing runtime in the form of a REST service for
 * hosting Spive Processes, one thread group per Process Instance.
 */
public class BasicRunnerGateway extends Gateway {
  public static final Logger LOG = LoggerFactory.getLogger(BasicRunnerGateway.class);
  private static final Jsonb jsonb = JsonbBuilder.create();
  private static final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private List<String> availabilityZones;

  public BasicRunnerGateway(final UmbilicalWriter umbilicus, final List<String> availabilityZones) {
    super(umbilicus);
    this.availabilityZones = availabilityZones;
  }

  /**
   * Intended for use in sporadic workloads only, nondeterministic is fine. (TODO restrict nicely
   * with typing)
   */
  public String lookup(final String availabilityZone) {
    if (!availabilityZones.contains(availabilityZone)) {
      throw new InternalException(
          "Unknown availability zone: "
              + availabilityZone
              + " - should not happen, the set of zones should be managed by Spive and static per process");
    }
    return "http://" + availabilityZone + ":8080/api/v0/";
  }

  public void startInstance(RunThreadGroupRequest request, String runnerUrl) {
    try {
      if (!umbilicus.getReplayMode()) {
        doStartInstance(request, runnerUrl);
      }
    } catch (Throwable t) {
      umbilicus.addError(t);
      throw t;
    }
  }

  private void doStartInstance(RunThreadGroupRequest request, String runnerUrl) {
    System.out.println("Starting " + request.threadGroup().name() + " on " + runnerUrl);
    // TODO idempotent?

    final URI requestUri = URI.create(runnerUrl + "/thread_groups").normalize();
    try {
      final HttpRequest.BodyPublisher requestBody =
          HttpRequest.BodyPublishers.ofString(jsonb.toJson(request), StandardCharsets.UTF_8);
      final HttpRequest httpRequest =
          HttpRequest.newBuilder(requestUri)
              .timeout(Duration.ofSeconds(5))
              .POST(requestBody)
              .build();
      final HttpResponse<String> httpResponse = sendRetrying(httpRequest);
      // TODO return and assert deterministic umbilicalUri?
      LOG.info("RunThreadGroupResponse: " + httpResponse.body());
    } catch (InterruptedException e) {
      // FIXME probably need to sprinkle accept() methods with `throws InterruptedException`
      Thread.currentThread().interrupt();
      throw new RuntimeException("Gateway interrupted", e);
    }
  }

  public void stopInstance(String name, String runnerUrl) {
    try {
      if (!umbilicus.getReplayMode()) {
        doStopInstance(name, runnerUrl);
      }
    } catch (Throwable t) {
      umbilicus.addError(t);
      throw t;
    }
  }

  private void doStopInstance(String name, final String runnerUrl) {
    // TODO idempotent? (noop if it already disappeared)?
  }

  // TODO encapsulate the generic retry in an AbstractHttpApiGateway... possibly... or
  // RetryingClient a la Armeria. Along with pooling, keepalives & reconnects, etc
  private HttpResponse<String> sendRetrying(HttpRequest request) throws InterruptedException {
    while (true) {
      try {
        final HttpResponse<String> response =
            client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          return response;
        }
        throw new Exception(
            "Expected HTTP 2xx, got " + response.statusCode() + " " + response.body());
      } catch (InterruptedException e) {
        throw e;
      } catch (Exception e) {
        umbilicus.addWarning(e);
        Thread.sleep(1000);
      }
    } // FIXME retry forever unless permanent
  }
}
