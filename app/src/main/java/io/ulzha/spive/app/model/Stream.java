package io.ulzha.spive.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reliably persisted, effectively append-only[1], near real-time consumable stream of Events.
 *
 * <p>Every Event in a Stream belongs to one of its (possibly many) Types. This set is fixed for the
 * lifetime of a Stream.
 *
 * <p>Each Stream normally has at most one Process appending to it - this Process is also
 * responsible for ensuring each newly appended Event is consistent with the history of events that
 * precede it, regarding business semantics of the given Stream.
 *
 * <p>A field or a list of fields are used as the Stream's Partition key. These fields must exist in
 * every Type of the Stream. Events in a Partition are written and consumed totally ordered by
 * EventTime. (Among Events from different Partitions the order is unspecified.)
 *
 * <p>A Stream holds attributes like a descriptive name, and an ownership (access control list)
 * record for the contained data.
 *
 * <p>[1] By "effectively append-only" we mean that, while instances shall have capabilities to only
 * append to logs of their output Stream, Spīve nevertheless manages multiple related versions of a
 * given Stream.
 *
 * <p>A patch upgrade of a producing application incurs a corresponding _patch_ version change of
 * its output Stream. So does a storage migration. (Consider that the appropriate storage choice for
 * each particular Stream depends on its creation and consumption patterns. These may change over
 * time, and Spīve platform may perform optimizations where the Stream data is migrated from one
 * storage to another.)
 *
 * <p>A minor upgrade of a producing application incurs a corresponding _minor_ version change of
 * its output Stream. So does a compaction, as well as an emergency edit.
 *
 * <p>A major upgrade of a producing application may create a new stream or incur a _major_ version
 * change of its output Stream. This is almost purely syntactic sugar: creating a new major version
 * of an existing Stream is largely equivalent to creating a new Stream with a new name.
 *
 * <p>A Process Instance, upon restart, may very well be replaying its Events from a different
 * version of the stream, i.e. a different set of underlying event logs. Application code should not
 * be aware of this happening. (Note that Streams are expected to contain identical events across
 * their patch version changes, whereas minor version changes imply observable differences in
 * events, and automated following of minor Stream version changes is opt-out.)
 *
 * <p>Since a new (minor or patch) Stream version often remains identical in large part to its
 * previous version, Spīve expresses version relationships by means of _forking_.
 *
 * <p>A Stream can be a fork _into_ an ancestor Stream, and Spīve supports highly assisted swapping
 * of a fork into the place of the original Stream (replacing a prefix of the Event sequence)
 * without triggering an immediate replay. Useful, in particular, for seamless migration of an
 * existing Process to an optimized or compacted version of its input. Applications opt out of
 * compaction if it is incompatible with their business logic. (If the writer and all its consumers
 * are compatible, then this enables automation to delete the original Events or archive them to
 * cold storage.)
 *
 * <p>A Stream can also be a fork _off of_ an ancestor Stream, which is a way to version _latest_
 * events of the ancestor Stream (replace a suffix of the event sequence). This typically functions
 * in tandem with spawning a new Process to replay the resulting sequence, useful for testing a
 * pre-release application version and verifying its behavior against the old version, and
 * eventually releasing the new version and deleting obsolete Events from the ancestor Stream.
 * (Especially relevant for recovering from incidents that have produced corrupt Events or
 * contradictory sequences.)
 *
 * <p>With versioning we can also work around corruption occurring in the persistent stores, such as
 * caused by hackers, cosmic rays, YOLO mode or whatnot.
 */
public class Stream {
  public String name; // human readable

  /**
   * major.minor.patch[-pre-release]
   *
   * <p>UI can reveal more structure when displaying version, including details like the operational
   * decision ("Tom debug issue #MMMM", "Nina edit incident #NNNN"), or the Process or application
   * version bump, which corresponds to the change.
   */
  public String version;

  // a name-version pair is a unique identifier platform-wide, and maps 1:1 to a valid id
  // (Type 5 UUID?)
  public UUID id;

  // public ForkPoint forkOff;
  // public ForkPoint forkInto;

  public UUID owningProcessId;

  public List<String> key = new ArrayList<>();

  public Stream(final UUID id, final String version) {
    this.id = id;
    this.version = version;
  }

  void setOwningProcessId(final UUID processId) {
    owningProcessId = processId;
  }

  @Override
  public String toString() {
    return "Stream{"
        + "name='"
        + name
        + '\''
        + ", version='"
        + version
        + '\''
        + ", id="
        + id
        + ", owningProcessId="
        + owningProcessId
        + ", key="
        + key
        + '}';
  }

  /**
   * Every partition falls in exactly one PartitionGroup. Each PartitionGroup is written to a single
   * EventLog, and the set of PartitionGroups is fixed for the lifetime of a Stream. (Regrouping by
   * forking can happen transparently on the fly as part of Spīve autoscaling.)
   */
  //  public static class PartitionGroup {
  //    public UUID streamId;
  //    public String id; // captures keyRange, so not a UUID
  //    // or maybe we don't need to encode keyRange, it just needs to be deterministically computed
  //
  //    /** Should perhaps focus on hash ring approach, not semantic ranges at field level? */
  //    public Map<String, ValueRange> keyRange;
  //
  //    public boolean contains(Event event) {
  //      return true;
  //    }
  //
  //    public EventLog eventLog;
  //
  //    public static class ValueRange {
  //      public String startValue; // inclusive
  //      public String stopValue; // exclusive
  //    }
  //  }

  public Stream() {
    this("unnamed");
  }

  public Stream(final String name) {
    this(name, UUID.randomUUID());
  }

  public Stream(final String name, final UUID id) {
    this.name = name;
    this.id = id;
  }
}
