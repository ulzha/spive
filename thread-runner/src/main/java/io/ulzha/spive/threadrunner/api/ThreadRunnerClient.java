package io.ulzha.spive.threadrunner.api;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ThreadRunnerClient {
  private static final Jsonb jsonb = JsonbBuilder.create();
  private final HttpClient client;
  private final URI uri;

  public ThreadRunnerClient(final HttpClient client, final URI uri) {
    this.client = client;
    this.uri = uri;
  }

  public GetThreadGroupHeartbeatResponse getHeartbeat() throws InterruptedException {
    HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
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
}
