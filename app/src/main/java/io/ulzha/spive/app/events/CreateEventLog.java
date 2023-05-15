package io.ulzha.spive.app.events;

import io.ulzha.spive.lib.EventTime;
import java.util.UUID;

public record CreateEventLog(
    // partition key
    UUID streamId, UUID logId, String partitionRange, EventTime start) {}

// TODO FinalizeEventLog?
// Something that records event time, explicitly after the owning instance positively reports
// finalizing it?
