package io.ulzha.spive.app.events;

import java.util.List;
import java.util.UUID;

public record CreateProcess(
    // partition key
    UUID processId,
    String name,
    String version,
    String artifactUrl,
    // String runner? String runnerType? UUID runnerId?
    // encodes runtimes? runner gateway classes? clusters? pools? discovery names?
    List<String> availabilityZones,
    List<UUID> inputStreamIds,
    List<UUID> outputStreamIds) {}
