package io.ulzha.spive.lib;

import io.ulzha.spive.core.BigtableEventStore;
import io.ulzha.spive.core.LocalFileSystemEventStore;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An append-only durable data structure, totally ordered by event time. (Akin to a Kafka partition,
 * BookKeeper ledger, a Kinesis shard, or an SQS message group.) May contain events from one or
 * several `Stream.Partition`s. (Even from multiple streams?)
 *
 * <p>Relies on concurrency primitives of the underlying store to safely detect races. Multiple
 * EventLog objects operating concurrently on the same underlying log is specifically safe, their
 * reads and appends occur atomically.
 *
 * <p>Storing several partitions together is an optimization, useful because Spive scalability and
 * simplicity depends on arbitrarily fine-grained partitioning, while the underlying storage may not
 * always happily manage the corresponding amount of small files or similar artifacts, and also
 * one's development environment may struggle with having to open objects/handles for myriads of
 * partitions.
 */
public interface EventLog extends Iterable<EventEnvelope>, AutoCloseable {
  Map<String, EventStore> eventStores = new ConcurrentHashMap<>();

  /**
   * @param connectionString
   * @param logId
   * @return a log that is seeked to its start and open for reading and appending.
   */
  static EventLog open(String connectionString, String logId) throws IOException {

    //    Class.forName(className)
    //        .getDeclaredConstructor()
    //        .invoke(null, (Object[]) args);
    EventStore eventStore = eventStores.computeIfAbsent(connectionString, EventLog::getEventStore);
    return eventStore.openLog(UUID.fromString(logId));
  }

  private static EventStore getEventStore(final String connectionString) {
    try {
      if (connectionString.startsWith("io.ulzha.spive.core.LocalFileSystemEventStore;")) {
        return new LocalFileSystemEventStore(connectionString);
      } else if (connectionString.startsWith("io.ulzha.spive.core.BigtableEventStore;")) {
        return new BigtableEventStore(connectionString);
        // should validate immediately? (as opposed to validating late, at an openLog attempt)
        // throw new RuntimeException("Failed to connect to event store", e);
      } else {
        throw new InternalException(
            "No such event store: "
                + connectionString
                + " - should never happen in operation, as Spive should correctly specify one of its supported event stores upon creation of a stream");
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Efficient interface for reading the log in sequence and appending to it. No removals supported.
   */
  public static interface AppendIterator extends Iterator<EventEnvelope> {
    /**
     * Tries to append {@code event} to the end of the log, validating that no other event is
     * appended in between it and the event that was previously returned from this iterator's {@code
     * next()}.
     *
     * <p>Relies on concurrency primitives of the underlying store to safely detect races.
     *
     * @param event
     * @return {@code event} if the iterator was at the end of the log, and {@code event} was
     *     appended directly after. Then {@code event} is also guaranteed to be returned by the next
     *     {@code next()} call. If {@code event} was not appended, due to iterator not yet being at
     *     the end of the log, then a different object, the actual next element read from the store,
     *     is returned resp. guaranteed to be returned by the next {@code next()} call.
     * @throws UnsupportedOperationException if this iterator is read-only
     * @throws IllegalArgumentException if {@code event.time} <= the event time of the latest event
     *     returned from this iterator's {@code next()}
     * @throws IllegalStateException if the iterator is at the end of the log and the log is closed
     */
    default EventEnvelope appendOrPeek(EventEnvelope event) {
      throw new UnsupportedOperationException("appendOrPeek");
    }
  }

  /**
   * The returned iterator is only supposed to be iterated through by one thread, such as by the
   * event loop of one Process Instance.
   */
  public AppendIterator iterator();

  /**
   * @return false if we have read all the events and the log is closed, true otherwise (which means
   *     it may have zero or more events to catch up with right now, and more can further be
   *     appended).
   */
  //  boolean mayHavePastEvents();

  /**
   * @return false if we have read all the events earlier than `present` and there is no possibility
   *     for more events to appear earlier than `present` (either the log is closed or it has some
   *     sensing/enforcing capability that tells that `present` is a perfect watermark).
   */
  //  boolean mayHavePastEvents(EventTime present);

  /**
   * Tries to append event to the end of the log.
   *
   * <p>Validates that its event time comes strictly after the latest event time already present.
   *
   * <p>This is primarily useful for output from stateless applications. Offers no mechanism for
   * ensuring the application has seen all the events already present in the log the moment a
   * subsequent event gets appended.
   *
   * <p>This method is thread-safe. (Workloads may issue writes concurrently.)
   *
   * @param event
   * @return true if event was appended, false if not because there is already a stored Event with
   *     time >= event.time.
   * @throws IOException
   */
  //  public boolean append(Event event) throws IOException;

  /**
   * Appends a time-adjusted version of event to the end of the log.
   *
   * <p>Time is adjusted if necessary to come strictly after the latest event time already present.
   *
   * <p>May employ concurrency primitives of the underlying store to optimize, i.e. persist the
   * event and retrieve the adjusted time in one operation (or as few operations as possible).
   *
   * <p>This is primarily useful for output from stateless applications. Offers no mechanism for
   * ensuring the application has seen all the events already present in the log the moment a
   * subsequent event gets appended.
   *
   * <p>This method is thread-safe. (Workloads may issue writes concurrently.)
   *
   * @param event
   * @return the event time which was decided in the append operation.
   * @throws IOException
   */
  EventTime appendAndGetAdjustedTime(EventEnvelope event) throws IOException;

  /**
   * Tries to append event to the end of the log, validating that no other Event is appended in
   * between it and the Event that was previously observed as being the last one.
   *
   * <p>By requiring awareness of prevTime, this allows an application to ensure it has seen all the
   * events already present in the log the moment a subsequent event gets appended. That way the
   * application can prevent duplicate events or events that form logical contradictions against
   * application state, even if the application has instances running redundantly that race their
   * appends against each other.
   *
   * <p>This method is thread-safe. (Workloads may issue writes concurrently with event handlers.)
   *
   * @param event
   * @param prevTime the time of the preceding event of this log (NB: not necessarily of the same
   *     Partition as event (but here we oughtn't even have Partition leaking in as a notion))
   * @return true if event was appended directly after the event that had time of prevTime, false if
   *     not because the latest stored Event has time > prevTime.
   * @throws IllegalArgumentException if event.time <= prevTime or if the latest stored Event has
   *     time < prevTime, or if the log is closed.
   */
  boolean appendIfPrevTimeMatch(EventEnvelope event, EventTime prevTime) throws IOException;
}
