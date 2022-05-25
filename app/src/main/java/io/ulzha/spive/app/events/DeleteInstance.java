package io.ulzha.spive.app.events;

import java.util.UUID;
import lombok.Data;

@Data
public class DeleteInstance {
  public UUID instanceId; // partition key
}
