package io.ulzha.spive.app.events;

import java.util.List;
import java.util.UUID;

public record CreateInstance(
    // partition key
    UUID processId,
    UUID instanceId,
    // Just point to shardId? Any reason for an Instance to exist outside a Shard?
    // TODO inputLogIds and inputPartitionRanges
    // Matters for owned/disowned generation
    List<String> partitionRanges,
    // How do we follow forks in a stream during one instance's lifetime? Probably upon creation the
    // entire Current can be specified TODO, from which deviation will be unnecessary
    // Always quasi-exclusive for write? To only race against replicas but not other writers? No
    // reason to restrict yet - we may find fused logs beneficial, for when dire contention is not
    // an issue?
    List<UUID> logIds,
    String workloads,
    String runnerUrl) {}
