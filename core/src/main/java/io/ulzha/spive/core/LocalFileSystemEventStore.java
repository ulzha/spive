package io.ulzha.spive.core;

import io.ulzha.spive.lib.EventStore;
import io.ulzha.spive.lib.InternalException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores events as newline separated JSON rows in files, in a directory tree structured as
 * `<path>/<logId>/events.jsonl`.
 *
 * <p>This can reside on local disk, or on e.g. Google Persistent Disk for more durability.
 *
 * <p>When a log is closed for appending, a special {} line is appended at its end.
 */
public final class LocalFileSystemEventStore implements EventStore {
  private static final Pattern CONNECTION_STRING_RE =
      Pattern.compile(".+" + ";basePath=(.+)" + "$");

  private final String basePath;
  private final Map<UUID, LocalFileSystemEventLog> eventLogs = new ConcurrentHashMap<>();

  public LocalFileSystemEventStore(final String connectionString) {
    final Matcher matcher = CONNECTION_STRING_RE.matcher(connectionString);

    if (!matcher.matches()) {
      throw new InternalException("Unexpected connectionString: " + connectionString);
    }

    final MatchResult matchResult = matcher.toMatchResult();
    this.basePath = matchResult.group(1);
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
