package io.ulzha.spive.app.events;

import java.util.List;
import java.util.UUID;

public record CreateInstance(
    // partition key
    UUID processId,
    UUID instanceId,
    String partitionRange,
    // How do we follow forks in a stream during one instance's lifetime? Or will that be
    // unnecessary?
    List<UUID> logIds,
    String workloads,
    String runnerUrl) {}
