package io.ulzha.spive.app.events;

import java.util.List;
import java.util.UUID;
import lombok.Data;

// Should manage in SpiveInventory land? KTLO does not need stream creation... But bootstrapping
// both in tandem then gets a bit more complex, at least annoying by the looks of it...
@Data
public class CreateStream {
  public UUID streamId; // partition key
  public String name;
  public String version;
  public List<String> eventSchemas;
  public String eventStore;
}
