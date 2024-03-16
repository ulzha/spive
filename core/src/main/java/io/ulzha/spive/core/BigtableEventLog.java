package io.ulzha.spive.core;

import static com.google.cloud.bigtable.data.v2.models.Filters.FILTERS;

import com.google.api.gax.rpc.ServerStream;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.ConditionalRowMutation;
import com.google.cloud.bigtable.data.v2.models.Mutation;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowCell;
import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventLog;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.InternalException;
import io.ulzha.spive.util.Json;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

public final class BigtableEventLog implements EventLog {
  private final BigtableDataClient dataClient;
  private final UUID logId;
  private static final String TABLE_ID = "event-store";
  private static final String EVENT_COLUMN_FAMILY = "event";
  // separate column for metadata - might or might not be useful for efficient access...
  private static final String METADATA_COLUMN_QUALIFIER = "metadata";
  private static final String PAYLOAD_COLUMN_QUALIFIER = "payload";

  public BigtableEventLog(final BigtableDataClient dataClient, final UUID logId) {
    this.dataClient = dataClient;
    this.logId = logId;
  }

  /**
   * Reads the next event after prevEvent.
   *
   * <p>Will block after the last row until more events are appended or the log is closed.
   *
   * @return the next event, or null to signify a closed log.
   */
  private EventEnvelope read(final EventEnvelope prevEvent) throws IOException {
    final String start = (prevEvent == null ? null : toRowKey(prevEvent.time()));
    final Query query =
        Query.create(TABLE_ID)
            .range(start, null)
            .filter(
                FILTERS
                    .chain()
                    .filter(FILTERS.limit().cellsPerColumn(1))
                    .filter(FILTERS.family().exactMatch(EVENT_COLUMN_FAMILY)));
    Row row = null;

    while (row == null) {
      final ServerStream<Row> stream = this.dataClient.readRows(query);
      final Iterator<Row> iterator = stream.iterator();
      if (iterator.hasNext()) {
        // TODO buffer ahead, instead of querying one at a time, for reasonable throughput
        stream.cancel();
        row = iterator.next();
      } else {
        try {
          Thread.sleep(1000);
          // TODO notification path, to act more quickly than this polling loop
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
      // TODO catch timeouts and other errors that are likely intermittent, and retry
    }

    final List<RowCell> cells = row.getCells();
    final String metadataJson = cells.get(0).getValue().toStringUtf8();

    if (metadataJson.length() != 0) {
      EventEnvelope event =
          Json.deserializeEventMetadata(metadataJson, cells.get(1).getValue().toStringUtf8());
      // TODO assert that each one is keyed exactly toRowKey(previous event time read)
      return event;
    } else {
      return null;
    }
  }

  /**
   * Bigtable does not offer the ability to do a reverse scan. So we do a bit of binary search to
   * beginning of time if there's no event >= hintEventTime, or to the end of time if the last event
   * is not found within X events from hintEventTime.
   *
   * @param hintEventTime used as a starting position
   * @return the last event time in the log.
   */
  //  private EventTime seekLast(final EventTime hintEventTime) throws IOException {
  //    throw new RuntimeException("not implemented");
  //  }

  @Override
  public EventTime appendAndGetAdjustedTime(final EventEnvelope event) throws IOException {
    // naively seek to the end and try appending, and repeat until no conflict
    //    do {
    //      EventTime
    //    } while ();
    throw new RuntimeException("not implemented");
  }

  @Override
  public boolean appendIfPrevTimeMatch(final EventEnvelope event, final EventTime prevTime) {
    if (event.time().compareTo(prevTime) <= 0) {
      throw new IllegalArgumentException("event time must come strictly after prevTime");
    }

    final String newRowKey = toRowKey(prevTime);
    final String metadataJson = Json.serializeEventMetadata(event);

    // sets cell only if it does not exist
    ConditionalRowMutation mutation =
        ConditionalRowMutation.create(TABLE_ID, newRowKey)
            .otherwise(
                Mutation.create()
                    .setCell(EVENT_COLUMN_FAMILY, METADATA_COLUMN_QUALIFIER, metadataJson)
                    .setCell(
                        EVENT_COLUMN_FAMILY, PAYLOAD_COLUMN_QUALIFIER, event.serializedPayload()));

    return !dataClient.checkAndMutateRow(mutation);
  }

  @Override
  public void close() {
    dataClient.close();
  }

  private String toRowKey(EventTime eventTime) {
    return logId + ":" + eventTime.toOrderPreservingString();
  }

  @Override
  public AppendIterator iterator() {
    return new AppendIteratorImpl();
  }

  private class AppendIteratorImpl implements AppendIterator {
    private EventEnvelope prevEvent;
    private EventEnvelope nextEvent;

    /** Will block after the last event until more events are appended or the log is closed. */
    @Override
    public boolean hasNext() {
      if (nextEvent == null) {
        try {
          nextEvent = read(prevEvent);
          if (prevEvent != null
              && nextEvent != null
              && nextEvent.time().compareTo(prevEvent.time()) <= 0) {
            throw new InternalException(
                String.format(
                    "Out-of-order event sequence: %s followed by %s in %s",
                    prevEvent.time(), nextEvent.time(), logId));
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      if (nextEvent == null) {
        close();
        return false;
      }
      return true;
    }

    @Override
    public EventEnvelope next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      prevEvent = nextEvent;
      nextEvent = null;
      return prevEvent;
    }

    @Override
    public EventEnvelope appendOrPeek(EventEnvelope event) {
      final EventTime prevTime = (prevEvent == null ? EventTime.INFINITE_PAST : prevEvent.time());
      if (BigtableEventLog.this.appendIfPrevTimeMatch(event, prevTime)) {
        return event;
      } else {
        if (!hasNext()) { // sets nextEvent
          throw new IllegalStateException(
              "expected a subsequent event after "
                  + prevTime
                  + ", but log prematurely indicates end");
        }
        return nextEvent;
      }
    }
  }
}
