package io.ulzha.spive.app.events;

import java.util.List;
import java.util.UUID;

// Should manage in SpiveInventory land? KTLO does not need stream creation... But bootstrapping
// both in tandem then gets a bit more complex, at least annoying by the looks of it...
public record CreateStream(
    // partition key
    UUID streamId, String name, String version, List<String> eventSchemas, String eventStore) {}
