package io.ulzha.spive.lib.umbilical;

import io.ulzha.spive.lib.EventTime;
import jakarta.annotation.Nullable;
import java.util.List;

public record HeartbeatSample(
    @Nullable EventTime eventTime,
    @Nullable String partition,
    List<ProgressUpdate> progressUpdates) {}
