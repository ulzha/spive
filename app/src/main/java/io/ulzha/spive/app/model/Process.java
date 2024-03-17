package io.ulzha.spive.app.model;

import io.ulzha.spive.app.model.agg.Timeline;
import io.ulzha.spive.lib.EventTime;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A distributed process. (Not to be confused with a process running on a single computer.)
 *
 * <p>Represents one deployment of an application code. Multiple Processes for one application name
 * may be deployed and run concurrently, such as CI test runs, ad-hoc runs by developers, and by
 * automatic optimization harnesses. (TODO say only one is exposed in service discovery? Per
 * hermetic domain)
 *
 * <p>A process may be stateless or stateful. Process Instances can have state in local memory,
 * which must be deterministically computed from the consumed Events.
 *
 * <p>A process can consume zero or more Streams of Events, and it can produce zero or one Stream.
 *
 * <p>Event consumption can fail (throw exceptions or propagate exceptions from a third party system
 * behind a Gateway), in which case event-sourced Processes never skip the problematic event, but
 * instead retry reliably. Spīve always tries to "keep the lights on" by replaying event history on
 * a copy of the failing Process Instance, which allows Processes to proceed through hardware
 * failures and similar. The replacement Process Instance replays the same partition range as the
 * original one, starting from the same position that the whole Process was started from, reliably.
 *
 * <p>Many processes can be consuming from the same Stream; a process can start consumption from the
 * very beginning of the Stream or from an arbitrary position in it.
 *
 * <p>A Process can be split into Shards for horizontal scaling. Each Shard consumes a different
 * subset of Partitions of the input Streams. (Similar to how Kafka consumers in a consumer group
 * would each be asigned a subset of partitions of the topic.) All the Shards run the same
 * application code. The union of inputPartitionRanges of all the Shards must match the set of
 * Partitions of the input Streams, unless the intent is to process only a subset of Partitions.
 *
 * <p>An Instance can have multiple identical copies always running as a redundancy measure, too, as
 * a way for an application to have active-active configuration for high availability. The replica
 * Instances that redundantly consume the same set of partitions are guaranteed to share eventually
 * consistent state, while Gateways perform side effects redundantly if idempotent, or otherwise all
 * but one replica stay in replay mode (i.e. active-active for reads but active-passive for
 * updates).
 *
 * <p>At any point in time, the Instances of one Process can be in different positions in the
 * consumed Stream, some may be in replay mode and some live. Spīve orchestrates them so that they
 * all eventually progress through to the latest Event in the Stream and become live, unless stopped
 * by permanent failures.
 */
public class Process {
  public String name; // human readable

  /**
   * Handle to application code version. E.g. a commit-ish on GitHub.
   *
   * <p>Oftentimes with more details attached, to denote CI test runs, ad-hoc runs by developers,
   * and by automatic optimization harnesses.
   *
   * <p>In UI this should probably also be accompanied by instance versions (or ranges, if multiple
   * are supported), so that they are easy to correlate with anomalies.
   */
  public String version;

  // a name-version pair is a unique identifier platform-wide, and maps 1:1 to a valid id
  // (Type 5 UUID?)
  public UUID id;

  // handle to application code package. E.g. a Docker image with a digest
  public String artifactUrl;

  public List<String> availabilityZones;

  public Set<Stream> inputStreams = new HashSet<>();
  public EventTime startTime; // same for all the input streams
  public Set<Workload> workloads = new HashSet<>();
  public Set<Stream> outputStreams = new HashSet<>();
  public Set<Gateway> gateways = new HashSet<>();

  // aggregate precomputed for frontend visualization purposes
  // aggregate of all the Instances timelines
  // TODO recalculate when an instance drops and is replaced with replay
  // may have to be preaggregated in groups if we're scaling lots... InstanceGroup,
  // InstanceGroupProgress?
  public Timeline timeline = new Timeline();

  @Override
  public String toString() {
    return "Process{"
        + "name='"
        + name
        + '\''
        + ", version='"
        + version
        + '\''
        + ", id="
        + id
        + ", artifactUrl='"
        + artifactUrl
        + '\''
        + ", availabilityZones="
        + availabilityZones
        + ", inputStreams="
        + inputStreams
        + ", startTime="
        + startTime
        + ", workloads="
        + workloads
        + ", outputStreams="
        + outputStreams
        + ", gateways="
        + gateways
        + ", instances="
        + instances
        + '}';
  }

  // should this be named Task, like in Samza?
  public static class Instance {
    public UUID id;
    public volatile Process process; // null if this instance has been deleted
    // ^ awkward but it's a convenience, null check equivalent to absence check in process.instances
    // artifact - could benefit from varying runner and packaging per Instance in rare cases?
    // version - varying runner versions and varying library/inventory versions must be recorded?

    private Instance() {}

    public Instance(UUID id, Process process, URI umbilicalUri) {
      this.id = id;
      this.process = process;
      this.umbilicalUri = umbilicalUri;
      timeoutMillis = 15 * 1000;
    }

    // a subset of full set of input partitions
    // public Set<String> inputPartitionRanges = new HashSet<>(); a bit redundant
    // public volatile Process.Shard shard?

    // a subset of workloads of the respective Process
    public Set<Workload> workloads = new HashSet<>();

    /**
     * Configurable by SpiveScaler, possibly varies across instances if some partitions require
     * beefier computation than others and get their dedicated instances...
     */
    public volatile int timeoutMillis;

    // Latest known successfully handled event time
    public volatile EventTime checkpoint;

    public volatile InstanceStatus status;

    // The link to our control plane BasicRunnerGateway which launches and tracks this instance.
    public URI umbilicalUri;

    // aggregate precomputed for frontend visualization purposes
    public Timeline timeline = new Timeline();

    @Override
    public String toString() {
      return "Instance{"
          + "id="
          + id
          + ", process="
          + process
          + ", workloads="
          + workloads
          + ", timeoutMillis="
          + timeoutMillis
          + ", checkpoint="
          + checkpoint
          + ", status="
          + status
          + ", umbilicalUri="
          + umbilicalUri
          + '}';
    }
  }

  /**
   * Besides event handling, an application may also comprise lightweight background workloads,
   * unpredictable query serving workloads, long-running heavy computation workloads, etc., which
   * benefit from being scalable independently of each other.
   *
   * <p>To facilitate easy and simple development of robust and performant applications, workload
   * code in Spīve can be written as part of the application and talk directly to its data model
   * (that is built in-memory by the way of handling input events).
   *
   * <p>The application code should not make assumptions as to which (if any) workload Runnables
   * coexist on a given Instance.
   *
   * <p>The workload Runnables should never modify the Instance's in-memory state nor create side
   * effects through Gateways, except emitting events through EventStoreGateway. (Unsure how to
   * enforce that, within realms of Java, without badly sacrificing simplicity.)
   */
  public static class Workload {
    public String name;

    public Workload(final String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return "Workload{" + "name='" + name + '\'' + '}';
    }
  }

  // A process is structured into Shards, which each run zero or more replica Instances.

  // The partitionRanges are not necessarily disjoint - e.g. during a scaling-up operation there
  // could be an old shard consuming all Partitions of the input, and two other shards whose
  // instances are warming up, each consuming half of Partitions.

  // Shard creation is a way to horizontally scale a process. Adjusting nInstances is a way to
  // increase redundancy. Also, in the use case of serving layer, bumping nInstances and having a
  // load balancer in front is a way to increase the serving throughput for a hot key.

  // App implementation does need to be aware how its scaling is structured. This ties in with
  // partition keys in a given app's Stream schemas. Spīve platform supports large scale processes
  // with million+ instances, and to feasibly structure this model, Spive app itself must be aware
  // that a Process is scaled horizontally beyond what one Spive.Watchdog can watch. (Constrained by
  // network throughput on a nominally bad day.) Each Spive instance only handles a subset of
  // instances of a Process, when said Process is sufficiently large.
  // TODO measure numbers and write up in README as well

  // Need the subset to exactly correspond to a (set of) Shards? Each Spive instance knowing the
  // full set of Shards in a Process? Otherwise detecting conditions such as "all instances have
  // started" or "all instances have stopped" is no longer event-time instantaneous... (in what way
  // do we build on that? Instantaneity simplifies away edge cases probably)

  // For now I suspect that state can hold the entire set of Instances of a Process, and each Spive
  // instance that owns a given Process can read all their events. (The instances collectively own a
  // Process then, but it's safe because of determinism, if a bit wasteful.) Owning an Instance
  // doesn't mean that we stay oblivious of sibling events, but we noop their side effects and we
  // don't dedicate workloads to them. (The latter is manual work, not handled under the hood by
  // platform.)

  // Optimize further with first class Shard only if measured numbers say that we should?
  // TODO measurer that could serve app developers as well
  public record Shard(Map<Stream, Stream.PartitionRange> partitionRanges, int nDesiredInstances
      // Map<Workload, n> nDesiredWorkloads
      ) {}

  // TODO CreateShard/DeleteShard
  // well, first need to lookup them somehow
  // Descriptor/Role/Request/Desire/Assignation perhaps? Also with active/standbyness?
  public Map<Shard, Set<Instance>> shards;

  public Set<Instance> instances = new HashSet<>();

  public Process(
      String name,
      String version,
      UUID id,
      String artifactUrl,
      List<String> availabilityZones,
      Set<Stream> inputStreams,
      Set<Stream> outputStreams) {
    this.name = name;
    this.version = version;
    this.id = id;
    this.artifactUrl = artifactUrl;
    this.availabilityZones = availabilityZones;
    this.inputStreams = inputStreams;
    this.outputStreams = outputStreams;

    final Shard defaultShard =
        new Shard(
            inputStreams.stream()
                .collect(
                    Collectors.toMap(key -> key, keyIgnored -> new Stream.PartitionRange("*"))),
            1);
    this.shards = Map.of(defaultShard, Set.of());
  }
}
