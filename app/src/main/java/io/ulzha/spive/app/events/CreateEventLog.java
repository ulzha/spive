package io.ulzha.spive.app.events;

import java.util.UUID;

public record CreateEventLog(
    // partition key
    UUID streamId, UUID logId, String partitionRange, String start) {}

// TODO FinalizeEventLog?
// Something that records event time, explicitly after the owning instance positively reports
// finalizing it?
