package io.ulzha.spive.app.events;

import java.util.List;
import java.util.UUID;

// CreateStrand? Current? A Stream is the abstraction identified by name? Analogously how an
// Application is identified by name and has multiple processes with distinct versions.
// Should manage in SpiveInventory land? KTLO does not need stream creation... But bootstrapping
// both in tandem then gets a bit more complex, at least annoying by the looks of it...
public record CreateStream(
    // partition key
    UUID streamId,
    String name,
    String version,
    // what representation?
    List<String>
        eventTypes, // a.k.a. schema? Or is one of those usable in abstract, for wire-format
    // agnostic case?
    String eventStore) {}
