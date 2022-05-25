package io.ulzha.spive.app.events;

import io.ulzha.spive.lib.EventTime;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateEventLog {
  public UUID streamId; // partition key
  public UUID logId;
  public String partitionRange;
  public EventTime start;
}

// TODO FinalizeEventLog?
// Something that records event time, explicitly after the owning instance positively reports
// finalizing it?
