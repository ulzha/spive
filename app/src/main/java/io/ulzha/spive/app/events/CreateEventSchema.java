package io.ulzha.spive.app.events;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Should manage in SpiveInventory land? KTLO does not need schema creation...
public record CreateEventSchema(
    // partition key
    UUID schemaId,
    String name,
    Map<String, String> fieldTypeTags,
    List<String> partitionKeyFields) {}
