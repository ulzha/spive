package io.ulzha.spive.basicrunner.api;

import io.ulzha.spive.lib.Gateway;
import io.ulzha.spive.lib.InternalException;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
  private static final HttpClient client = HttpClient.newHttpClient();
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
      final HttpRequest httpRequest = HttpRequest.newBuilder(requestUri).POST(requestBody).build();
      final HttpResponse<String> response =
          client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      // TODO RunThreadGroupResponse.class?
      // TODO return and assert deterministic umbilicalUri?
      LOG.info("RunThreadGroupResponse: " + response.body());
      if (response.statusCode() != 201) {
        // FIXME retry forever
        throw new RuntimeException("Expected HTTP 201 Created, got " + response.statusCode());
      }
    } catch (IOException e) {
      // FIXME retry forever (unless permanent)
      throw new RuntimeException(e);
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
}