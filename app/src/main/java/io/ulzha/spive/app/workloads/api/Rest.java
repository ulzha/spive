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

  public record ProgressTile(
      // Control plane event time. Later tiles supersede earlier ones.
      EventTime snapshotTime,
      UUID processId,
      UUID instanceId, // if null then it's aggregated over all instances

      // Window in application event time.
      // Lengths currently come from a fixed set:
      // * per second
      // * per minute (used at the default dashboard zoom level, 5 pixels per minute)
      // * per hour
      // * per day (UTC)
      // * per year (grr, inconsistent lengths, exotic calendars)
      // (could generalize into milliseconds or millennia for yet unclear use cases)
      // A wide screen may hold 10ish hours, so we may want to keep minutely windows to cover that.
      // Or maybe that's the job of a cache, platform shall only keep 60... And have ability to
      // reconstruct on demand.
      // Keep minutely for every lagging instance, so catchup progress is visible.
      // Compaction effects... Visible when?
      EventTime windowStart, // inclusive
      EventTime windowEnd, // exclusive
      int nInputEventsUnknown, // > 0 would mean blurred
      int nInputEventsIncoming,
      // "events in progress" blinking would be just a front-end cheat, rendered from the difference
      // between newest and preceding tiles

      int nInputEventsOk,
      int nInputEventsWarning,
      int nInputEventsError,
      int nOutputEvents
      // unsure how output gateway errors would be represented when workloads spontaneously attempt
      // to emit
      ) {}

  // bulk state fetcher for loading the applications page (TODO later by dashboard id... Or that
  // aggregation happens in a separate DashboardPrecomputer app)
  // subsequent tiles should be filled in by SSE
  @Get("/progress")
  public HttpResponse progress() {
    final List<ProgressTile> response = null;
    return HttpResponse.ofJson(response);
  }
}
