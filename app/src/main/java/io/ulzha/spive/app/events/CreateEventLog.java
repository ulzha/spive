package io.ulzha.spive.app.events;

import java.util.UUID;

public record CreateEventLog(
    // partition key
    UUID streamId, UUID logId, String partitionRange, String start) {}

// TODO CreateFork with a list?
// A slew of CreateEventLog simultaneously decribe creation of a Fork - roundabout and possibly
// scales badly? Alt. non-simultaneously...

// TODO FinalizeEventLog? SealEventLog?
// Something that records event time, explicitly after the owning instance positively reports
// finalizing it?
