package io.ulzha.spive.app.events;

import java.util.List;
import java.util.UUID;

public record CreateProcess(
    // partition key
    UUID processId,
    String name,
    String version,
    String artifact,
    // String runner, // apollonetes or springbootio, or dataflow, or ...
    // encodes runtimes? runner gateway classes? clusters? pools? discovery names?
    List<String> availabilityZones,
    List<UUID> inputStreamIds,
    List<UUID> outputStreamIds) {}
