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
import io.ulzha.spive.app.lib.SpiveOutputGateway;
import io.ulzha.spive.app.model.Platform;
import io.ulzha.spive.app.model.agg.Timeline;
import io.ulzha.spive.lib.EventTime;
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
    // FIXME prevent duplicate creation events by design... Does that require generation of GUIDs
    // here in handlers?

    final UUID uuid = UUID.randomUUID();
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
        () ->
            !(platform.processesByApplicationAndVersion.containsKey(name)
                    && platform.processesByApplicationAndVersion.get(name).containsKey(version))
                && !platform.processesById.containsKey(uuid),
        event)) {
      return HttpResponse.ofJson(HttpStatus.CREATED, uuid);
    }
    throw HttpStatusException.of(HttpStatus.CONFLICT);
  }

  public record TileSnapshot(
      // Control plane event time. Later tiles supersede earlier ones.
      EventTime snapshotTime,
      UUID processId,
      UUID instanceId, // if null then it's aggregated over all instances
      Timeline.Tile tile) {}

  // bulk state fetcher for loading the applications page (TODO later by dashboard id... Or that
  // aggregation happens in a separate DashboardPrecomputer app)
  // subsequent tiles should be filled in by SSE
  // will also need events that invalidate a range of tiles? (As when compacted)
  @Get("/timeline")
  public HttpResponse progress() {
    final List<TileSnapshot> response = null;
    return HttpResponse.ofJson(response);
  }
}
