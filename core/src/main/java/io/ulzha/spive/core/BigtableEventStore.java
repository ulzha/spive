package io.ulzha.spive.core;

import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import io.ulzha.spive.lib.EventStore;
import io.ulzha.spive.lib.InternalSpiveException;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores streams in Google Cloud Bigtable, one row per event, using `<logId>:<prevEventTime>` as
 * the row key.
 *
 * <p>(Should include stream id too? "Long row keys take up additional memory and storage and
 * increase the time it takes to get responses from the Cloud Bigtable server..." The platform can
 * maintain mapping from streams to logs in its state instead...)
 *
 * <p>Use of previous event time lets appendIfPrevTimeMatch be easily implemented using
 * CheckAndMutateRow. Thus it only takes one RPC per append and there is no need to mutate rows
 * thereafter. The first event in a log is stored at key `<logId>:0`.
 *
 * <p>When a log is closed for appending, a special empty sentinel row is stored at key
 * `<logId>:<lastActualEventTime>`.
 */
public final class BigtableEventStore implements EventStore {
  private static final Pattern CONNECTION_STRING_RE =
      Pattern.compile(
          ".+"
              + ";projectId=([a-z0-9_-]{0,63})"
              + ";instanceId=([a-z0-9_-]+)"
              + "(?:;hostname=([a-z0-9_-]+);port=([0-9]+))?"
              + "$");

  // clients are expensive, so an instance only creates one per store (i.e. one per connection
  // string)
  private final BigtableDataClient dataClient;
  private final Map<UUID, BigtableEventLog> eventLogs = new ConcurrentHashMap<>();

  /**
   * Two formats supported:
   *
   * <p>
   *
   * <ul>
   *   <li>projectId=company-infra;instanceId=spive-prod-0 - real
   *   <li>projectId=user-dev;instanceId=spive-dev-0;hostname=localhost;port=8086 - emulator
   * </ul>
   */
  public BigtableEventStore(final String connectionString) throws IOException {
    final Matcher matcher = CONNECTION_STRING_RE.matcher(connectionString);

    if (!matcher.matches()) {
      throw new InternalSpiveException("Unexpected connectionString: " + connectionString);
    }

    final MatchResult matchResult = matcher.toMatchResult();
    final String projectId = matchResult.group(1);
    final String instanceId = matchResult.group(2);
    final String hostname = matchResult.group(3);
    final int port = Integer.parseInt(matchResult.group(4));

    BigtableDataSettings.Builder builder =
        (hostname == null
            ? BigtableDataSettings.newBuilder()
            : BigtableDataSettings.newBuilderForEmulator(hostname, port));
    builder.setProjectId(projectId);
    builder.setInstanceId(instanceId);

    this.dataClient = BigtableDataClient.create(builder.build());
  }

  @Override
  public BigtableEventLog openLog(final UUID logId) {
    return eventLogs.computeIfAbsent(logId, theId -> new BigtableEventLog(dataClient, theId));
  }
}
