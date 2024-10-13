package io.ulzha.spive.core;

import static com.google.cloud.bigtable.data.v2.models.Filters.FILTERS;

import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.ConditionalRowMutation;
import com.google.cloud.bigtable.data.v2.models.Mutation;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowCell;
import com.google.protobuf.ByteString;
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

  private Iterator<Row> query(final EventEnvelope prevEvent) {
    final String start = toRowKey(prevEvent == null ? EventTime.INFINITE_PAST : prevEvent.time());
    final Query query =
        Query.create(TABLE_ID)
            .range(start, endRowKey())
            .filter(
                FILTERS
                    .chain()
                    .filter(FILTERS.limit().cellsPerColumn(1))
                    .filter(FILTERS.family().exactMatch(EVENT_COLUMN_FAMILY)));
    return dataClient.readRows(query).iterator();
  }

  /**
   * Reads the next event after prevEvent.
   *
   * <p>Will block after the last row until more events are appended or the log is closed.
   *
   * <p>Note: AppendIteratorImpl is a more efficient way to read many events sequentially, as it
   * keeps ServerStream open.
   *
   * @return the next event, or null to signify a closed log.
   */
  // private EventEnvelope read(final EventEnvelope prevEvent)

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

    final boolean existed = dataClient.checkAndMutateRow(mutation);

    if (existed) {
      final Row row = dataClient.readRow(TABLE_ID, newRowKey);
      if (row == null) {
        throw new IllegalStateException(
            "row was reported existing but now is missing - should not happen unless someone messes with table contents");
      }
      if (row.getCells().get(0).getValue().toStringUtf8().isEmpty()) {
        throw new IllegalStateException("log is closed");
      }
    }

    return !existed;
  }

  @Override
  public void close() {
    // TODO explicitly cancel all iterator ServerStreams that may have not been fully consumed?
  }

  private String toRowKey(EventTime eventTime) {
    return logId + ":" + eventTime.toOrderPreservingString();
  }

  private String endRowKey() {
    return logId + ";";
  }

  @Override
  public AppendIterator iterator() {
    return new AppendIteratorImpl();
  }

  private class AppendIteratorImpl implements AppendIterator {
    private Iterator<Row> serverStreamIterator;
    private EventEnvelope prevEvent;
    private EventEnvelope nextEvent;

    public AppendIteratorImpl() {
      this.serverStreamIterator = query(prevEvent);
    }

    /**
     * Reads the next event after prevEvent.
     *
     * <p>Will block after the last row until more events are appended or the log is closed.
     *
     * @return the next event, or null to signify a closed log.
     */
    private EventEnvelope read() throws IOException {
      // System.out.println("Entering read() in " + Thread.currentThread() + " on " + this + "'s " +
      // serverStreamIterator + " with " + prevEvent);
      while (!serverStreamIterator.hasNext()) {
        System.out.println("In clumsy sleep loop...");
        try {
          Thread.sleep(1000);
          // TODO notification path, to act more quickly than this polling loop
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
        serverStreamIterator = query(prevEvent);
        // TODO catch timeouts and other errors that are likely intermittent, and retry
      }

      final Row row = serverStreamIterator.next();
      requireMatch(row, toRowKey(prevEvent == null ? EventTime.INFINITE_PAST : prevEvent.time()));
      final List<RowCell> cells = row.getCells();
      final String metadataJson = cells.get(0).getValue().toStringUtf8();

      if (metadataJson.length() != 0) {
        EventEnvelope event =
            Json.deserializeEventMetadata(metadataJson, cells.get(1).getValue().toStringUtf8());
        return event;
      } else {
        return null;
      }
    }

    private void requireMatch(final Row row, final String expectedKey) {
      final ByteString actualKey = row.getKey();
      if (!actualKey.isValidUtf8() || !actualKey.toStringUtf8().equals(expectedKey)) {
        throw new InternalException(
            String.format(
                "Corrupt row key sequence: the row after %s should be keyed %s but was %s",
                prevEvent.time(),
                "'" + expectedKey + "'",
                (actualKey.isValidUtf8()
                    ? "'" + actualKey.toStringUtf8() + "'"
                    : "invalid utf8, " + actualKey)));
      }
    }

    @Override
    public boolean wouldBlock() {
      // unsure if there is a need to be explicit about blocking I/O (isReceiveReady) as well? Or
      // need new name?
      if (nextEvent != null || serverStreamIterator.hasNext()) {
        return false;
      }
      // unneeded? The above already tells almost certainly whether there are more events?
      // Figure later if and where we too uninterruptibly wait
      // final ServerStream<Row> headServerStream = query(prevEvent);
      // try {
      //   return !headServerStream.iterator().hasNext();
      // } finally {
      //   headServerStream.cancel();
      // }
      serverStreamIterator = query(prevEvent);
      return !serverStreamIterator.hasNext();
    }

    /** Will block after the last event until more events are appended or the log is closed. */
    @Override
    public boolean hasNext() {
      if (nextEvent == null) {
        try {
          nextEvent = read();
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
      return nextEvent != null;
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
      if (event.time().compareTo(prevTime) <= 0) {
        throw new IllegalArgumentException(
            "event must have time later than that of the preceding event");
      }

      if (BigtableEventLog.this.appendIfPrevTimeMatch(event, prevTime)) {
        nextEvent = event;
      } else {
        if (!hasNext()) { // sets nextEvent
          throw new IllegalStateException(
              "expected a subsequent event after "
                  + prevTime
                  + ", but log prematurely indicates end");
        }
      }
      return nextEvent;
    }
  }
}
