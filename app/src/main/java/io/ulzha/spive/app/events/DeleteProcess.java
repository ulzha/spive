package io.ulzha.spive.app.events;

import java.util.UUID;
import lombok.Data;

@Data
public class DeleteProcess {
  public UUID processId; // partition key
}
