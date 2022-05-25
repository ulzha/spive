package io.ulzha.spive.core;

import io.ulzha.spive.lib.EventStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores events as newline separated JSON rows in files, in a directory tree structured as
 * `<path>/<logId>/events.jsonl`.
 *
 * <p>This can reside on local disk, or on e.g. Google Persistent Disk for more durability.
 *
 * <p>When a log is closed for appending, a special {} line is appended at its end.
 */
public final class LocalFileSystemEventStore implements EventStore {
  private final String basePath;
  private final Map<UUID, LocalFileSystemEventLog> eventLogs = new ConcurrentHashMap<>();

  public LocalFileSystemEventStore(final String basePath) {
    this.basePath = basePath;
  }

  @Override
  public LocalFileSystemEventLog openLog(final UUID logId) {
    return eventLogs.computeIfAbsent(
        logId,
        theId -> {
          try {
            var parentPath = Paths.get(basePath, theId.toString());
            Files.createDirectories(parentPath);
            var filePath = parentPath.resolve("events.jsonl");
            return new LocalFileSystemEventLog(filePath);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }
}
