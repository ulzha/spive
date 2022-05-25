package io.ulzha.spive.lib;

import java.io.IOException;
import java.util.UUID;

public interface EventStore {
  /** Returns the same EventLog object if logId matches. */
  EventLog openLog(final UUID logId) throws IOException;
}
