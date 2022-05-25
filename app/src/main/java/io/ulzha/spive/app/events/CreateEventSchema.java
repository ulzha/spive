package io.ulzha.spive.app.events;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

// Should manage in SpiveInventory land? KTLO does not need schema creation...
@Data
public class CreateEventSchema {
  public UUID schemaId; // partition key
  public String name;
  public Map<String, String> fieldTypeTags;
  public List<String> partitionKeyFields;
}
