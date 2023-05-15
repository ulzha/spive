package io.ulzha.spive.app.events;

import java.util.UUID;

public record DeleteInstance(
    // partition key
    UUID instanceId) {}
