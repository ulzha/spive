package io.ulzha.spive.app.events;

import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateInstance {
  public UUID processId; // partition key
  public UUID instanceId;
  public String partitionRange;
  // How do we follow forks in a stream during one instance's lifetime? Or will that be unnecessary?
  public List<UUID> logIds;
  public String workloads;
  public String runnerUrl;
}
