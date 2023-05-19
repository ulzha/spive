package io.ulzha.spive.app.model;

import io.ulzha.spive.lib.EventTime;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
 * <p>A Process can be split into Slices (? shards? better name? process as an instance group? do we
 * even need this abstraction yet?) for horizontal scaling. Each Slice consumes a different subset
 * of Partitions of the input Stream. (Similar to how Kafka consumers in a consumer group would each
 * receive a subset of messages on the topic.) All the Slices run the same application code.
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

  public String artifact; // handle to application code package. E.g. a Docker image with a digest
  public List<String> availabilityZones;

  public Set<Stream> inputStreams = new HashSet<>();
  public EventTime startTime; // same for all the input streams
  public Set<Workload> workloads = new HashSet<>();
  public Set<Stream> outputStreams = new HashSet<>();
  public Set<Gateway> gateways = new HashSet<>();

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
        + ", artifact='"
        + artifact
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
    // artifact - could benefit from varying runner and packaging per Instance in rare cases?
    // version - varying runner versions and varying library/inventory versions must be recorded?

    private Instance() {}

    public Instance(UUID id, Process process) {
      this.id = id;
      this.process = process;
      umbilicalUri = URI.create("http://foo");
      timeoutMillis = 15 * 1000;
    }

    public Set<UUID> inputPartitionIds = new HashSet<>();
    // Descriptor/Role/Assignation perhaps? Also with sliceId and master/slaveness?

    public Set<Workload> workloads =
        new HashSet<>(); // a subset of workloads of the respective Process

    /**
     * Configurable by SpiveScaler, possibly varies across instances if some partitions require
     * beefier computation than others and get their dedicated instances...
     */
    public volatile int timeoutMillis;

    // Latest known successfully handled event time
    public volatile EventTime checkpoint;

    public volatile InstanceStatus status;

    // The link to our control plane ThreadRunnerGateway which launches and tracks this instance.
    public URI umbilicalUri;

    @Override
    public String toString() {
      return "Instance{"
          + "id="
          + id
          + ", process="
          + process
          + ", inputPartitionIds="
          + inputPartitionIds
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

  //  public class Slice {
  //    Set<String> partitionIds;
  //    int nInstances;
  //  }
  //
  //  // A process is structured into Slices, which each run zero or more replica Instances.
  //  // The union of all the partitionIds in slices must match the set of Partitions of the input,
  //  // unless the intent is to ignore some Partitions.
  //  // The partitionIds are not necessarily disjoint - e.g. during a scaling-up operation there
  // could
  //  // be an old slice consuming all Partitions of the input, and two other slices that are
  // warming
  //  // up, each consuming half of Partitions.
  //  // Slice creation is a way to horizontally scale a process. Adjusting nInstances is a way to
  //  // increase redundancy. Also, in the use case of serving layer, bumping nInstances and having
  // a
  //  // load balancer in front is a way to increase the serving throughput for a hot key.
  //  public Set<Slice> slices;

  // The union of all the inputPartitionIds in instances must match the set of Partitions of the
  // input stream, unless the intent is to process only a subset of Partitions.
  public Set<Instance> instances = new HashSet<>();

  public Process(String artifact) {
    this.artifact = artifact;
  }
}
