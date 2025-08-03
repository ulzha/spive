package io.ulzha.spive.app.workloads.api;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.HttpStatusException;
import com.linecorp.armeria.server.annotation.ConsumesJson;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.Put;
import com.linecorp.armeria.server.annotation.RequestObject;
import io.ulzha.spive.app.events.CreateProcess;
import io.ulzha.spive.app.model.Platform;
import io.ulzha.spive.app.model.Process;
import io.ulzha.spive.app.model.agg.Timeline.Scale;
import io.ulzha.spive.app.spive.gen.SpiveOutputGateway;
import io.ulzha.spive.lib.EventTime;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public record Rest(Platform platform, SpiveOutputGateway output) {
  public record ProcessEntry(String name, UUID id) {}

  @Get("/applications")
  public HttpResponse applications() {
    final List<ProcessEntry> response =
        platform.processesById.values().stream()
            .map(process -> new ProcessEntry(process.name, process.id))
            .collect(Collectors.toList());
    return HttpResponse.ofJson(response);
  }

  public record CreateProcessRequest(
      String artifactUrl,
      List<String> availabilityZones,
      List<UUID> inputStreamIds,
      List<UUID> outputStreamIds) {}

  @Put("/applications/{name}/{version}")
  // possible to assume this regardles of header, and avoid "No suitable request converter found"?
  @ConsumesJson
  public HttpResponse applications(
      @Param("name") String name,
      @Param("version") String version,
      @RequestObject CreateProcessRequest request) {
    // FIXME nontrivial generation of UUID, to make the event belong to the intended partition
    final UUID uuid = UUID.randomUUID();

    try {
      validateArtifactUrl(request.artifactUrl());
    } catch (IOException e) {
      throw HttpStatusException.of(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    final CreateProcess event =
        new CreateProcess(
            uuid,
            name,
            version,
            request.artifactUrl(),
            request.availabilityZones(),
            request.inputStreamIds(),
            request.outputStreamIds());
    if (output.emitIf(
        // TODO ensure that the output stream exists
        // TODO ensure that there are no cycles of streams
        () ->
            !(platform.processesByApplicationAndVersion.containsKey(name)
                    && platform.processesByApplicationAndVersion.get(name).containsKey(version))
                && !platform.processesById.containsKey(uuid),
        event)) {
      return HttpResponse.ofJson(HttpStatus.CREATED, uuid);
    }
    throw HttpStatusException.of(HttpStatus.CONFLICT);
  }

  private void validateArtifactUrl(String artifactUrl) throws IOException {
    final String jarUrl = "jar:" + artifactUrl.split(";")[0] + "!/";
    final URL url = new URL(jarUrl);
    final URLConnection connection = url.openConnection();

    // TODO merge with basic-runner's validation function?
    // Jar would not necessarily be reachable from control plane. Runner's validation can be awaited
    // by control plane, it can see heartbeat not started. Here we just check well-formedness maybe?
    // For methods and modifiers validation,
    // https://docs.oracle.com/javase/tutorial/deployment/jar/jarclassloader.html what this doing?
    if (!(connection instanceof JarURLConnection)) {
      throw new IOException("Not a JAR URL");
    }
    ((JarURLConnection) connection).getManifest();
    // manifest.getEntries().keySet().stream()
    //     .forEach(key -> System.err.println("I just had a manifest entry: " + key));

    // The jar should be kept in state? Shared with runners from there?... Just a checksum surely?
    // Generally, external large objects handling in apps logic needs a facilitating mechanism
  }

  // maybe should just be a mapper tweak, not an extra boilerplate record?
  public record TileForFrontend(
    long windowStart,
    long windowEnd,
    long nOutputEvents) {}

  public record TileSnapshot(
      // Control plane event time. Later tiles supersede earlier ones.
      // TODO concurrent structure for that contract
      EventTime snapshotTime,
      UUID processId,
      UUID instanceId, // if null then it's aggregated over all instances
      TileForFrontend tile) {}

  // bulk state fetcher for loading the applications page (TODO later by dashboard id... Or that
  // aggregation happens in a separate DashboardPrecomputer app)
  // subsequent tiles should be filled in by SSE
  // will also need events that invalidate a range of tiles? (As when compacted)
  // @Get("/timeline")

  @Get("/process/{id}/timeline")
  public HttpResponse processTimeline(@Param("id") String id, @Param("start") String start,
                              @Param("stop") String stop, @Param("resolution") String resolution) {

    final UUID processId = UUID.fromString(id);
    final Process process = platform.processesById.get(processId);
    final List<TileSnapshot> response = new ArrayList<>();

    for (var tileEntry: process.timeline.tiles.get(Scale.MINUTE).entrySet()) {
      var tile = tileEntry.getValue();
      response.add(new TileSnapshot(
          null,
          processId,
          null,
          new TileForFrontend(tile.windowStart().getEpochSecond(), tile.windowEnd().getEpochSecond(), tile.nOutputEvents())));
    }
    return HttpResponse.ofJson(response);
  }
}
