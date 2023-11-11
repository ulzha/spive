package io.ulzha.spive.app.events;

import java.util.List;
import java.util.UUID;

public record CreateInstance(
    // partition key
    UUID processId,
    UUID instanceId,
    // Just point to shardId? Any reason for an Instance to exist outside a Shard?
    List<String> partitionRanges,
    // How do we follow forks in a stream during one instance's lifetime? Probably upon creation the
    // entire Current can be specified TODO, from which deviation will be unnecessary
    List<UUID> logIds,
    String workloads,
    String runnerUrl) {}
