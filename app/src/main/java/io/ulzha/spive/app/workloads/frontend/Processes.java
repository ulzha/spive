package io.ulzha.spive.app.workloads.frontend;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.ulzha.spive.app.model.Platform;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Helper defining simplified data structures used in API responses, and functions to populate them
 * from model.
 *
 * <p>Should just do gRPC-Web?
 */
public class Processes {
  private final Platform platform;

  public Processes(final Platform platform) {
    this.platform = platform;
  }

  public List<ProcessEntry> list() {
    // FIXME concurrent
    return platform
        .processesById
        .values()
        .stream()
        .map(process -> new ProcessEntry(process.name, process.id))
        .collect(Collectors.toList());
  }

  // Intellij sees them as read, spotbugs doesn't
  @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
  public static class ProcessEntry {
    public final String name;
    public final String id;

    public ProcessEntry(String name, final UUID id) {
      this.name = name;
      this.id = id.toString();
    }
  }
}
