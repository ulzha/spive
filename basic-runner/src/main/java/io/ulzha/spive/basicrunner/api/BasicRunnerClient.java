package io.ulzha.spive.basicrunner.api;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

// BasicRunnerObserver? Package together or otherwise clarify relationship with BasicRunnerGateway?
public class BasicRunnerClient {
  private static final Jsonb jsonb = JsonbBuilder.create();
  private final HttpClient client;
  private final URI uri;

  public BasicRunnerClient(final HttpClient client, final URI uri) {
    this.client = client;
    this.uri = uri;
  }

  public GetThreadGroupHeartbeatResponse getHeartbeat() throws InterruptedException {
    final HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
    try {
      final HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
      }
      return jsonb.fromJson(response.body(), GetThreadGroupHeartbeatResponse.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public GetThreadGroupIopwsResponse getIopws(final Instant start) throws InterruptedException {
    final URI uri = this.uri.resolve("iopws" + (start == null ? "" : "?start=" + start.toString()));
    final HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
    try {
      final HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
      }
      return jsonb.fromJson(response.body(), GetThreadGroupIopwsResponse.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
