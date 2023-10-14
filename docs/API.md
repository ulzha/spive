## API

(Should this be split into SDK and API? API as the endpoints that Spive exposes for scaling, optimization and such.)

TODO example layout of an application, explaining IoC

### Event handlers

Event handlers is what you would be primarily writing. Event handlers can read and write in-memory state.

This is where an application should perform its side effects (via [gateways](#gateway)). Note that event handlers tend to get executed many times over throughout a process lifetime. (E.g. every time we scale up to new instances or replace a lost one, a replay is carried out.) Duplicate side effects rarely appear though, because Spīve toggles gateways to dry-run mode when a replay is ongoing.

(TODO elaborate on determinism)

#### Event

Events are up to the application developers to define - plain old Java objects is a common choice. They must have the key fields particular to a given Stream, and any number of additional fields. EventTime is always tracked by Spīve, so there is no need to define a field for it.

#### Gateway

Side effects are wrapped by gateways.

Backpressure is handled by Gateways so that throttling and scaling decisions can be made by Spīve automatically. TODO elaborate

(TODO Gateways contribute towards optimizing throughput a bit? They could be batching together side effects from multiple instances on the same node... Side effects like Bigtable writes, Pub/Sub publishing and similar multiputs/multigets. The asynchronicity involved should not leak out of the Gateway API.)

(TODO elaborate on idempotence... What if create, set property, then delete, then replay from "set property" - should it succeed again?)

#### Stream

(Streams are managed by the platform, not much to see here.)

("managed ledgers" a useful term, cf. http://pulsar.apache.org/docs/en/concepts-architecture-overview/#managed-ledgers, except cursors managed as part of a Process, and multiple writers possible?)

#### Subscription

(There is no such thing as subscription in terms of API - nothing to see here. Note that, unlike in Google Cloud Pub/Sub, data retention does not belong in a subscription - events are retained in a stream indefinitely, regardless of the lifecycle of process instances reading from the stream. Internally, every instance has a set of stream partitions it is reading from, and the read position in each is maintained by Spīve. When an instance is deleted, this information goes away with it - there is indeed no such notion as a subscription with a lifecycle separate from that of an instance.)

TODO point to explanation of semantics (in the Contract section?) nevertheless

#### Type

Types are where serialization/deserialization rules live. An application deployment fails if its event handlers have incompatible signatures with the types of events in its input stream(s).

### Workloads

Aside from event handling, an application may also comprise lightweight background workloads, unpredictable query serving workloads, long-running heavy computation workloads, etc. Spīve allows you to develop such varied kinds of workloads all referencing the same in-memory model for state, and yet scale them independently of each other.

NB: Workloads must not modify the instance state in memory directly. The allowed way is to emit Events to an output log which is also an input to the instance, so they will be consumed by the [Event handlers](#event-handlers).

TODO consolidate from Process.java and SpiveInstance.java

TODO caution against side effects in workloads?

#### Snapshot (scheduled) workload

Think a cron job.

Spīve runs a snapshot workload serially with Event handlers, i.e. when the workload executes, the instance state available in memory constitutes a point-in-time snapshot, free from any modification. (TODO elaborate - why first class support? Why synchronization in a sporadic workload wouldn't cut it?)

Apart from emitting events, Spīve does not make any provision to preserve state from one snapshot workload invocation to another. (External stores could be used for that.)

You can schedule a snapshot workload on a regular basis in terms of event time. (TODO sidenote - processing time scheduling not useful? Preferably handled by a sporadic workload?)

This comes in particularly handy as a simple and easy means to produce robust snapshots/dumps/reports. Spīve caters for at least once execution.

TODO what happens when they run indefinitely? Configurable timeout policies with sensible defaults? Or merely ensure that another instance is making progress handling events without the long-running workload?

#### Concurrent (sporadic) workload

Think RPC handler code that serves requests.

NB: concurrent workloads are the only part of Spīve API where multithreading comes into play, from the standpoint of application code. Thus care must be taken to read in-memory state in a thread-safe manner, in case it is being concurrently modified by Event handlers.

Concurrent workloads are well suited for initiating nondeterministic changes, such as incoming write requests, or random events, or wall-clock timing, or changes captured by polling the outside world. In the case of an RPC, consider that you have the option to send a response immediately after the workload successfully emits a change Event, or you may prefer to do it asynchronously after handlers have altered the instance state in memory and carried out the respective side effects. The latter can be useful for populating your response with details from the freshly updated state, if needed.

The concurrent workload Runnable is started once per instance, and it may spawn many threads, e.g. for processing individual requests. The threads run concurrently with each other and with other workloads (concurrent or snapshot) and Event handlers.

### Process

TODO

(perhaps rename to Programs, to not cause confusion with distributed systems literature where a process is usually local?)

Think distributed process with durable execution.

(“process formation” a useful term, cf. https://12factor.net/concurrency, https://12factor.net/admin-processes, except stateful)

### Consistency Model

TODO elaborate (inspiration: https://github.com/reactive-streams/reactive-streams-jvm#specification)

The guarantees of the consistency model depend on application code adhering to certain constraints - see the [Contract](#contract) section below.

### Contract

This is the overview of constraints that application code should/must adhere to. Keep these in mind as a developer - some items are important for the above consistency model to correctly hold and they are *not always enforced* by the API type signatures or Spīve platform.

TODO Document a nice two-sided list, what concerns don't exist, what concerns do.

* hide (in order to avoid nondeterministic, non-portable artifacts):
  - partition subset assigned to the instance
    This means we can't generate random ids in response to CreateFoo calls, as the id could land in a partition which we aren't processing. (Instead the requester could pass a random UUID so the request gets routed to an instance that processes that partition... Or we could recursively request for them...)
  - geographic location of the instance
  - whether we're in replay or performing side effects for real
  - whether we're co-located with workloads, and which ones
  - etc. The business logic shouldn't consume anything nondeterministic
    This includes stuff like instance hostname, service account or any other identifiers lying around, information about resources available to the instance, metrics, input event rate as measured by wall clock, yada yada.
* don't need
  - no serializability of functions
  - no thread-safety for event handlers and snapshot workloads
  - no substantial constraints on logging, metric collection, build system, ...
  - no geographical limitation of instances to a particular region
* do need
  - wrap side effects in gateways that take care of the mundane (replay a.k.a. dry-run mode, retries, throttling/backoff, undo)
  - determinism in event handlers
    Event handlers may perform deterministic activities, including posting to external services via gateways (which retry indefinitely until success, or crash out on permanent failures), but otherwise they must not act dependent on chance.
    (TODO writeup on state of the art? https://medium.com/@cgillum/common-pitfalls-with-durable-execution-frameworks-like-durable-functions-or-temporal-eaf635d4a8bb -> static code analyzers)
    That's what concurrent workloads are for.
    (TODO helpers for creation from event handlers? via emitSelect/emitCase/emitConditional/emitUnpredictable hardly applicable in active-active... Or is that a higher level workflow API in making? A "lightweight decider" pattern also possible?)
  - thread-safe data structures, if consumed by concurrent workloads
  - design your streams + your in-memory data model with sharding in mind (i.e. event handlers do not operate on the global snapshot but a local one) and with lack of transactions in mind (referential integrity will break, and the conventional thing to do is to emit immediate consequential events propagating downstream app dependency graph, which is not necessarily the same way as upward in the entity aggregation graph... TODO is there a generic method for orienting the relations?)
  - write a compaction job, if replay of the entire history per shard is too costly (useful even if we hypothetically get snapshotting working)


### Best practices

#### Stream as an API

  Stream as an API in one direction. (In simpler terms - look at the dependencies in process DAG and make a decision what reacts to what... In other words - sagas, sagas everywhere?)

  RPC APIs in... TODO which directions? ~Ingress/egress/3rd party, and for signals that are rarely used, or hard to compute, thus don't warrant precomputed streams?

  Separate stream for each Type - TODO pros/cons?

#### Partitioning is arbitrarily fine-grained

  Do partition by whatever primary key you would use in a database. (A business entity ID, like user ID or such. Cf. [workflow IDs in Temporal](https://docs.temporal.io/workflows#workflow-id).)

  TODO elaborate

  Events from the same partition are always processed in order. (Note that the order of events from different partitions is not deterministic, even when processed on one instance. In particular some partitions may crash, and thus suspend their event handling indefinitely, while others proceed ahead in event time.)

  In application event handlers, spinning up multiple threads for additional parallelism rarely makes sense, as throughput can be boosted by sharding to more instances instead, with the added benefit of more predictable behavior.

#### No illegal states or bogus events

  Duplicates and contradictory events in the stream are preventable and you should do so

  There shouldn't be another CreateFoo(id=6, ...) event in your stream if you already have a Foo with id 6 in your state at that point in event time. And there shouldn't be a DeleteFoo(id=4) event if there is no Foo with id 4 in existence in your state at that point in event time. And there shouldn't be a CreateComment(author='user1') event if user1 has been suspended from commenting at that point in event time (this holds even when user events are in a different stream written by another application owned by a different team. Probably best to have a dedicated CommentingAllowed stream as the API between teams).

  This is commonly achieved by having a sporadic workload that serves HTTP or gRPC requests, synchronizes with the instance when it finds that the particular request is a state mutation (a _command_ in [CQS](https://en.wikipedia.org/wiki/Command–query_separation) terms), checks the actual state (a point-in-time snapshot at that point), and only appends the corresponding event if the mutation does not form a contradiction in business logic. This synchronization is not terribly contended, given that the application is appropriately massively sharded.

  TODO how about coordinating between multiple partitions though... Every process to coordinate must have its own state variable tracked in a stream, and a clear direction of causes and consequences... (Need an example of many-to-many coordination, Cartesian explosion?)

#### Side effects follow events

  A CreateFireball event handler would be expected to open the fuel valve and ignite it in some sequence, and exit with success when the ball is ablaze. An ExtinguishFireball event handler would close the fuel valve and exit with success when the fire has been put out.

  You can add code in your event handler to flag successful completion in application state, too, if you desire that your application's workloads receive such feedback (thus a <abbr title="Backend for Fireworks Frontend">BFF</abbr> can notify user's browser to replace the spinner with a fire emoji, for example).

  This is not to preclude emitting additional events of the kind "FireballCreated" and "FireballExtinguished", but as per the above, often it is enough to just have events that initiate side effects. By convention, name these events in imperative tense.

#### Application versioning

  Rollouts of new versions of your application are facilitated with zero downtime, zero event loss, and zero manual coordination

  There is first class support for three flavors of version upgrades, as outlined below.

##### *0. Input*

  In process properties the application version has input stream versions attached to it. These are bumped, without stopping the process, whenever the platform swaps out its underlying input - as is the case when its producing application undergoes version change, or the stream itself undergoes automated maintenance like sharding or compaction. As such, this does not really mark a new version of your application but merely helps risky event visibility.

  In the absence of egregious bugs, stream versioning should not break the business contracts of their consuming applications. (Though it must be noted that a minor stream version change is prone to cause operational effects, in the sense that even though business logic of the application stays the same, its inputs do change to a new, corrected, world view.)

  Operations-wise, an input upgrade often occurs without an immediate redeployment (replay) of your process instances. Instances which have outlived the upgrade will have their "input" component still refer to the old input stream, different from the process "input" version. Your application's input upgrade does not result in a new version of its output stream, thus it is not specially indicated in your consumer dashboards in the UI.

##### *i. Patch*

  A "patch" upgrade refers to when you deploy a new version of your application merely to fix a performance problem or fix its logic in a fully backward compatible way (e.g., emit improved detected language messages for audios for the future). The names and schemas of your output streams do not change. You have established that the output events that your earlier versions created are still correct as far as your contract with your downstream applications goes, in other words they do not need to be bulk updated or revoked.

  For this case, Spīve lets you launch the new version of the application with replay from the beginning of its input streams, compare outputs with the previous version to verify whatever aspects you would like, and at a certain point in event time swap the new version's output in place of the original output stream, transparently to downstream applications and without causing a replay in them.

  Operations-wise, a patch upgrade results in the appearance of a new input version in your consumer dashboards, with corresponding color-coding in the UI, which helps them troubleshoot, in case the upgrade does trigger bugs.

##### *ii. Minor*

  A "minor" upgrade refers to a bug fix, or a logic change, whereby the name and schema stays backward compatible, along with the business contract around your output, but the history of events needs to be overwritten. E.g. there may be buggy events you have to delete as if they never happened, or new/late events that you have to retroactively splice in at the correct points in event time.

  For this case, Spīve lets you spin up the new version of the application with a new version of its output stream, and it will automatically propagate the effects to downstream applications, by creating new instances and replaying from the beginning of the new version. This incurs recomputation cost (which can be estimated) but should incur no downtime or manual work (unless downstream applications have opted out of automated minor version following).

  It may sometimes be faster to produce the new output stream from the old one by means of a "migration script" application and afterwards perform a patch upgrade to the intended application logic.

  Operations-wise, a minor upgrade results in the appearance of a new input version in your consumer dashboards, with corresponding color-coding in the UI, which helps them troubleshoot, in case the upgrade does trigger bugs. Caveat regarding [semver](https://semver.org/): a minor upgrade yields an observable change of Stream contents, so operationally a minor upgrade is not necessarily frictionless/backwards compatible for consumers, yet the semantics of those contents are backwards compatible, which is why we name it "minor".

##### *iii. Major*

  A "major" upgrade refers to changes where your new outputs are established to not be compatible with the existing consumers at all (because of changes to your application output schema or semantics).

  In such cases it is appropriate to create a new application writing to a new major version of the old stream, or to a wholly new output stream with a new name. Spīve assists consumer migration away from the old stream by visualizing deprecated streams in the UI, and providing recomputation cost and time estimates.

##### *iv. YOLO*

  This refers to modifying streams (upserting events instead of appending only) or deploying new application code to a preexisting process. This breaks Spīve's assumptions and voids consistency guarantees. Useful perhaps if the application owners have reasoned through and implemented whatever guarantees necessary in their own way.

  In particular, stateless applications may freely deploy new versions like this, because they don't have state to be kept consistent with the history of events. (TODO unsure how much of this should be supported, as Spīve's raison d'être is management of stateful applications.)

#### Infinite retry as opposed to dead-letter queues

  In an incident where some events get stuck due to failures, Spīve will retry indefinitely, unless you stop the process or deploy a fixed version of your application code (a patch or minor upgrade, as per above). The UI provides a summary (estimated percentage of partitions blocked, a sample of keys that are blocked) for your convenience. As soon as a fix is in place, the stuck events will eventually get piped into the application automatically - you shouldn't ever need to manually manage ad-hoc reprocessing.

  (Compare this to the concept of a dead-letter queue, which is a way for a stream processing system to skip processing stuck events after a finite number of retries, and put them in a separate queue/topic for later investigation. In trying to benefit from event-sourcing consistency guarantees, such skipping is not quite permissible, because as a result, the order of events would be altered.)

  There are two levels of retry, one happening inside gateway calls (indefinite until a call returns with a permanent failure, or the instance crashes), and another in the form of respawning of crashed/stuck instances, done by Spīve platform (highly robust because the platform offers redundancy). Thus, in application code, rolling your own retry rarely makes sense.

  The partitions which aren't blocked by stuck events will generally progress alright throughout an incident, as Spīve avoids rigid bundling, and instead facilitates arbitrarily fine-grained partitioning (see above). Healthy partitions can proceed while blocked ones are being healed automatically to an extent (instances respawned, scaled to add resources, etc), or undergo manual troubleshooting.
