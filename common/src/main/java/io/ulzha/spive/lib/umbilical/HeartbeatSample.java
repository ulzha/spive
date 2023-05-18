package io.ulzha.spive.lib.umbilical;

import io.ulzha.spive.lib.EventTime;
import java.util.List;
import javax.annotation.Nullable;

public record HeartbeatSample(
    @Nullable EventTime eventTime,
    @Nullable String partition,
    List<ProgressUpdate> progressUpdates) {}
