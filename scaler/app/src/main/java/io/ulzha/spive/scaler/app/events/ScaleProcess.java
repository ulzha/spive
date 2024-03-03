package io.ulzha.spive.scaler.app.events;

import java.util.UUID;

public record ScaleProcess(
    // partition key
    UUID processId
    // etc etc etc
    ) {}
