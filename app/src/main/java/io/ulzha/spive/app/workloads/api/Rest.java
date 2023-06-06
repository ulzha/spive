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
    System.out.println("Maybe emitting " + event);
    System.out.println("Map: " + platform.processesByApplicationAndVersion);
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
}
