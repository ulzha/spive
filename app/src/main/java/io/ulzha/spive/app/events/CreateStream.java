package io.ulzha.spive.app.events;

import java.util.List;
import java.util.UUID;

// CreateStrand? Current? A Stream is the abstraction identified by name? Analogously how an
// Application is identified by name and has multiple processes with distinct versions.

// A Current could be the abstraction, belonging to a Stream, that Processes own (write) and
// consume. They are slightly variable, in that they get automatically forked, compacted, etc. All
// forks/straights belong to a Stream (or even multiple streams), and are append-only, whereas a
// Current is defined as a subset of those, and the subset is changeable.
// "Current Current version" - clumsy wording? "Channel" - collision?

// Should manage in SpiveInventory land? KTLO does not need stream creation... But bootstrapping
// both in tandem then gets a bit more complex, at least annoying by the looks of it...

// Likely Spive KTLO would not even model Applications or Streams, it would work with Processes and
// Currents, and this would be CreateFork/CreateStraight. It's looking like the right place for
// eventStore fixation as well. One Stream would then be portable across stores while staying at the
// same human-specified version.
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
