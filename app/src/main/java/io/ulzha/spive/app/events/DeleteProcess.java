package io.ulzha.spive.app.events;

import java.util.UUID;

public record DeleteProcess(
    // partition key
    UUID processId) {}
