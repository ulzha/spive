package io.ulzha.spive.app.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p>A storage migration or repartitioning of any Stream incurs at least one new straight, with a
 * corresponding _fork id_ change.[2]
 *
 * <p>TODO Compaction really a numeric bump, or no? 1.4c30.2.12 -> 1.4c31.0.0? Or put it after
 * patch, 1.4.2.31.12 - so minor and compaction bumps share the upgrade impact? So do patch and
 * fork? Stylize accordingly, as minor with a subscript and patch with a subscript? Well, compaction
 * does not have to cause replay anywhere, does it? Just 1.4.2.43 is also alluring, but it hides the
 * fact that data _is_ observably different...
 *
 * <p>TODO need a way to refer to the as-if-never-compacted version, as it is the audit trail?
 *
 * <p>A patch upgrade of a producing application incurs a corresponding _patch_ version change of
 * its output Stream.
 *
 * <p>A minor upgrade of a producing application incurs a corresponding _minor_ version change of
 * its output Stream. So does an emergency edit.
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
 *
 * <p>TODO first class forks/straights? And rewrite the forking above... or not? A Stream maintains
 * a sequence of forks, so that a fork id uniquely identifies a history line through the graph of
 * prior forks. A new Stream may then be initialized with a pointer to existing Stream's fork
 * sequence, to implement versioning without having to duplicate the forks or their contents. (Any
 * sort of archiver would have to navigate this carefully to not archive straights that are on
 * future lines of present or future readers of the Stream.)
 *
 * <p>[1] By "effectively append-only" we mean that, while instances shall have capabilities to only
 * append to logs of their output Stream, Spīve nevertheless manages multiple related versions of a
 * given Stream.
 *
 * <p>[2] Consider that the appropriate storage choice for each particular Stream depends on its
 * creation and consumption patterns. These may change over time, and Spīve platform may perform
 * optimizations where the straights are migrated/copied from one storage to another. One possible
 * course of action:
 *
 * <ul>
 *   <li>batch fast copy an existing version up to certain event time, and save that as a new fork 1
 *       into the old
 *   <li>then create a new unfinalized fork 2 off of the same event time
 *   <li>spawn instances of the application consuming the copy and outputting to fork 2 and let them
 *       catch up
 *   <li>migrate downstream consumers to fork 2 as well, and finally
 *   <li>kill all old instances off.
 * </ul>
 *
 * Or even just one disconnected straight (connected at INFINITE_PAST...) The build of the affected
 * applications must include gateway for the new storage - may be an additional preparation step.
 *
 * <p>As a less cautious alternative, a big-bang finalize-and-fork at a given event time can also be
 * coordinated, and does not need instances duplicated. How to stagger? Warmup, where instance
 * assists by ramping up outputs and reads to a new gateway, part of coordination? This probably
 * lower prio than the approach with duplicate instances.
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

  // this is relevant for app developer initiated processes? Maintenance processes may exist writing
  // to forks of the same "stream"
  public UUID owningProcessId;

  public List<String> key = new ArrayList<>();

  /**
   * major.minor.patch.infork.outfork would be the real input stream (Current) version, where the
   * last two numbers are indexes of fork in this array... This gets managed somewhere outside Spive
   * though; Straights and (possibly implicit) Currents constitute the API?
   */
  // public List<Fork> forks = new ArrayList<>();

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
   * Each PartitionRange is written to a single EventLog, and the set of PartitionRanges is fixed
   * for a streak. (Can we call Stream versions that? Or Ledgers? Forks? Braids? Straights? Reaches?
   * Histories? Traverses? They carry name that is the Stream name. Or File name.) Over the course
   * of a given streak, every partition falls in exactly one PartitionRange.
   *
   * <p>Repartitioning by forking can happen transparently on the fly as part of Spīve autoscaling.
   */
  public static record PartitionRange(String id // captures keyRange, so not a UUID
      ) {}
  // or maybe we don't need to encode keyRange, it just needs to be deterministically computed
  //
  //    /** Should perhaps focus on hash ring approach, not semantic ranges at field level? */
  //    public Map<String, ValueRange> keyRange;
  //
  //    public boolean contains(Event event) {
  //      return true;
  //    }
  //
  //    public static class ValueRange {
  //      public String startValue; // inclusive
  //      public String stopValue; // exclusive
  //    }

  // public EventTime start?
  // public EventTime end?
  public Map<PartitionRange, UUID> eventLogIds;

  public Stream() {
    this("unnamed");
  }

  public Stream(final String name) {
    this(name, UUID.randomUUID());
  }

  public Stream(final String name, final UUID id) {
    this.name = name;
    this.id = id;
    this.eventLogIds = new HashMap<>();
  }
}
