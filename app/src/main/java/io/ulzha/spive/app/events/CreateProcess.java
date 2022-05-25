package io.ulzha.spive.app.events;

import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class CreateProcess {
  public UUID processId; // partition key
  public String name;
  public String version;
  public String artifact;
  // public String runner; // apollonetes or springbootio, or dataflow, or ...
  // encodes runtimes? runner gateway classes? clusters? pools? discovery names?
  public List<String> availabilityZones;
  public List<UUID> inputStreamIds;
  public List<UUID> outputStreamIds;
}
