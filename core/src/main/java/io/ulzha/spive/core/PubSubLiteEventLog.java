package io.ulzha.spive.core;

import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventLog;
import io.ulzha.spive.lib.EventTime;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

public final class PubSubLiteEventLog implements EventLog {
  //  private final UUID logId;

  public PubSubLiteEventLog(final UUID logId) {
    //    this.logId = logId;
  }

  @Override
  public EventTime appendAndGetAdjustedTime(final EventEnvelope event) throws IOException {
    throw new RuntimeException("not implemented");
  }

  @Override
  public boolean appendIfPrevTimeMatch(final EventEnvelope event, final EventTime prevTime)
      throws IOException {
    throw new RuntimeException("not implemented");
  }

  @Override
  public void close() throws Exception {}

  @Override
  public Iterator<EventEnvelope> iterator() {
    throw new RuntimeException("not implemented");
  }
}
