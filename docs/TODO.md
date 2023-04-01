## TODO

### Hard problems

- [ ] Try not to overengineer and reinvent Kubernetes
- [ ] Weak event time ordering - what to do when event times match across partitions/streams, and get assigned to one instance. Order by partition key - nope, not stable enough if we want to support partition ranges with holes? Maybe combine in a migration process that rewrites with new tiebreaker values (we would need to take care to hide the tiebreaker due to its nondeterministic value)? Force a new minor version of process when changing range assignment to a coarser one for any reason? Force output partitioning finer than the product of input partitionings? Order by lineage (causality) and not merely lexicographical - i.e. some sort of quasi-fixed, "ontological" order component implied between time and tiebreaker when consuming input, and reified into output tiebreaker in emitConsequential?
- [ ] What stats to collect for ensuring steady progress, so we can compact and archive them?

### Exciting problems

- [ ] Graph the arrivals smoothly with ~5s delay (even smaller on focus)
- [ ] Visualize errors (first occurrence of a blocker, by exception type, and the fraction of partitions affected)
- [ ] Scroll left to seamlessly pan to a lower resolution historical graph
- [ ] Have UI quickly filter/query streams by time span or key/partition (tour de force - generate code example for doing the same thing as UI does in CLI, Scio, BigQuery, etc)
- [ ] KubernetesRunner, InProcessThreadRunner
- [ ] 5 minute "Getting Started" guide + a HelloWorld app - how to generate app.lib, develop and run locally. (Cookiecutter kind of thing but able to function offline, once cached)
- [ ] Have UI provide debugging instructions (locally, remotely)
- [ ] Examples of tests (event definition language, named snapshots as JUnit rules, capturers, matchers) - some examples of likely and non-obvious bugs included
- [ ] Example with best-effort graceful shutdown for workloads and gateways (just to clarify the extent of non-guarantee)
- [ ] An example change data capture (CDC) application (sorting notifications with 1 h delay? Dataflow job triggered at watermark and doing appendAndGetTime for late events, aided by an hourly batch scan?) Debouncing perhaps useful in some cases?
- [ ] Example advanced distributed data structure implementation (real time toplist?)
- [ ] Visualize workload logs (and metrics) somehow in between events as well... or just link to Stackdriver? Champion golden signals, aligned to timeline (with its warning and error hues), embedded from 3rd party sources like that? Should have a way to view logs sorted by event time?
- [ ] Example interactive application, web page updating itself until you see your writes (React/Redux?) (Also where does the fanout lie for building one result set from a massively sharded backend? In a sidecar? Or rather, fan-in updates to an index in front, like ElasticSearch, and get away with only single gets from backend?)
- [ ] Compute checksums and sanity check for nondeterminism (error on replay - alt. warning if tolerated... Of a suppressed kind?)
- [ ] Compute diffs between streams
- [ ] Example patch upgrade (involves a pre-release fork that gets promoted to a patch version when it has been verified working)
- [ ] Example minor upgrade (involves inspection of diffs)
- [ ] Example major upgrade flow (must be trivial to add a new field to CreateFoo event, for example. New Stream - copy EventSchemas -> edit? Then copy Stream with defaults? When to deploy the process? What to do with partially applied side effects when handlers of the old process are interrupted?)
- [ ] Support distributed synchronization of snapshot workloads across shards (may fail on network partition. Scheduling in advance increases reliability)
- [ ] Pause function ("wait for input" workload shorthand?) in UI to pause a partition/instance/the whole world at a given event time, indefinitely - useful for scheduled maintenance of external systems, debugging, etc.
- [ ] Example of point-in-time consistent operation on all child entities from app SDK (relies on a synchronization solution in the control plane, as per above)
- [ ] Examples of incident recovery (a simple edit, a clock jump/drift would be interesting, or a widespread Bigtable outage/corruption, triggering backup failover)
- [ ] Maintain a read-only copy in a LocalFileSystemEventLog to recover quicker and make regular restarts (new code deployments) cheap. (Even when facing a network split? We want to avoid Spīve failure cascading to apps.)
- [ ] SpiveScaler accepting only strategies, not hard-configured values. Support custom autoscalers as code, targeting cost/latency objectives, taking budget, request rate, etc, as well as historical state into account. Forecast a few hours ahead instead of merely trailing. Default strategy to autoscale to budget. (Redeploy/catchup throughput throttled as a separate position in budget?)
- [ ] SpiveScaler supporting overrides (lower latency for planned demo circumstances, more instances for a planned launch, force single instance for simple low tier applications, etc)... Respect manual overrides made directly via Spive UI during an incident when SpiveScaler is down? Do not be concerned with scaling of event stores - they belong to inventory?
- [ ] Intuitive UI for creation of sandboxes, frictionless launch and cleanup of multiple deployments of an application. Just a few options - select the version to deploy, select whether all side effects should be redone (from start or from a point in event time) (or should only the deviating ones be redone/undone, or none), select whether a whole new copy of inventory should be provisioned (i.e. a hermetic sandbox), select debugging options (e.g. enable debug flag/socket, pause at a given event time/condition, leave instances alive after exiting normally)
- [ ] An example CustomBlueGreenDeployment application showcasing how GHE events and Spīve API enables custom infrastructure automation for orchestrating your own application(s) deployments and their configurations. Generally useful for fleet management, ad-hoc experimentation, incident handling etc. Can watch the underlying for patterns in unhappy path events, timing out partitions, expedite scaling, or imperatively optimize (not to be confused with business logic/signals)
- [ ] SpiveOptimizer as a separate infra application? (Or yet another workload rather? Prefer unidirectional control flow, so keep in the same application if it writes to its own input?)
- [ ] Advanced retry behavior (somewhere between SpiveScaler and SpiveOptimizer?) that includes splitting of partition ranges, throttling, bumping of system resources, etc. - automate of [menial manual work](https://sre.google/sre-book/eliminating-toil/) and alleviate on-call stress.
- [ ] Example optimization rules (throttle calls toward external systems when they don't offer backpressure; less urgent batch workloads to execute in off-peak hours; transparently geo-replicate streams at some level; save money by lazily spinning up instances for unimportant partitions; ...)
- [ ] Service discovery... (How to balance with support for sharding? Path-based routing enough? Or should we look at StatefulSet and GKE ingress instead?)
- [ ] Unify service discovery with inventory: if something exposes endpoints then it is inventory. Configuration-wise, declaring endpoints as inventory would cause the registration of those endpoints in service discovery. (Should the platform take care of hiding instances from discovery during replay? Or should the application/workload temporarily redirect to its proxy/discovery authority when in replay? Leaky... The proxy could maybe add a header showing a conservative lag estimate, and let clients state Accept-Lag.)
- [ ] For manual overrides (configuration updates over umbilical) during incidents, Spive UI needs to be able to hit the instance that owns any given application shard, even if discovery is down along with SpiveInventory. Harden to have state more durable? Broadcast?
- [ ] PoC Backstage plugin
- [ ] PoC dependency visualization, wannabe data mesh
- [ ] PoC blocking path visualization, Improved Pipeline Overview style - be able to tell whether something is stuck in transcoding or what. (In dependency visualization UI or perhaps in 3rd party tracing UI?)
- [ ] Basic tracing of causal chains to Lightstep/Datadog/New Relic, without needing trace IDs explicitly added by application developers (is EventTime a span? Is causal graph another sort of span?)
- [ ] An example scheduler application (CliccTracc)
- [ ] An example load tester application (for a gateway)
- [ ] An example custom environment runner (GPU, Azure, ...)
- [ ] An example Python application (Trio for async workloads? Requests hopefully support it already? "generally asyncio simply prints and discards unhandled exceptions from Tasks")
- [ ] An example Scala application
- [ ] Configurable timeouts/moneyouts, per time interval, and/or per event
- [ ] Document the [contract](doc/API.md#Contract), a nice two-sided list of what concerns don't exist and what concerns do
- [ ] Document migration from 3rd party event-sourcing systems, a tutorial/script on how to import change data stored in any ordered/timestamped format of your choice
- [ ] Test helpers for parameterizing over various modes of parallelization and replay (https://tech.redplanetlabs.com/2021/03/17/where-were-going-we-dont-need-threads-simulating-distributed-systems/)
- [ ] SpiveBench as a separate infra application (running another application and testing for performance and correctness issues etc. when it comes to high event rates, extreme sharding, swapping storages...)
- [ ] Regular recovery testing, imitating a cold start of the production world (have some acceptance criteria to not replay everything always - say, at least important keys making progress end to end)
- [ ] SpiveChaos as a separate infra application (killing resources, corrupting storages, skewing clocks, redeploying with duplication of side effects, crashing gateway calls, and otherwise testing the overall platform for resiliency)
- [ ] "Expand all" for an entity - e.g. for a process, see deployment structure with replicas, see cost, KPIs, etc, which would normally be hidden under a UI button
- [ ] Collect histogram of cost over version, performance over version. The point is to show trends/anomalies along the version dimension too, not just over wall clock time or event time. (Control for effects of inventory changes to ensure comparison is fair? Test performance in overlapping time frames and not spread out in time, so that external systems can be expected to be running a stable version and to be roughly in the same state.)
- [ ] Expand all automatic maintenance done - retry bursts, error pattern analysis, history of sharding decisions, history of scaling/throttling (non)decisions with human-readable reasons (visualize expected effects ahead of time, as annotations on graphs in the future - important because scaling takes time), history of sanity checks, history of profiling runs, history of storage and runtime optimizations
- [ ] Expand with the control plane timeline filtered to a particular process, color coded by version (overlay with cost & performance histograms)
- [ ] Sharding heuristics: hot key set (for independent scaling), erroring key set and its complement (though this maybe doesn't need to result in a sharding change), ...
- [ ] Sanity heuristics: any lagging instance always crashing despite the application purportedly being active-active HA? Spawning new threads in event handlers? Statical analysis of the jar/deployable artifact (alert and reject applications using EventLog or other IO sidestepping the generated interface)?
- [ ] Search the dashboard (not just process names, also codesearch and even event data)
- [ ] Visualize dependencies between processes and streams in the dashboard
- [ ] Visualize groups of related processes as entities, and relations between them
- [ ] Separate SpiveFancyLayout application? Visualize consequences always from left to right, mostly from top to bottom; each entity roughly clumped as a horizontal slice(s) and colored; ownership roughly clumped as a vertical slice(s) and circled. Clump arrow slopes for 2.5D+ perception. Seamlessly pan left from dashboard
- [ ] Visualize meta dependencies - platform, its bootstrap platform, ad infinitum, processes with their replication factors, inventory (particularly interesting if shared) by panning further
- [ ] Visualize stream version history (seamlessly pan to a drawer in UI)
- [ ] Visualize dependencies between individual events processed, also sequence diagram style; enable referencing with a "common" or "happy" path
- [ ] Advanced tracing - visualize event journey with propagation of its effects, with a sharable animated replay for troubleshooting
- [ ] Process with offset time into past - useful for profiling or performance testing, by spawning a process consuming event traffic from a particularly busy time interval in the past (possibly accelerated by a constant factor), and measuring side effect throughput or request throughput. Also opens up a possibility to simulate real-time consumption of events when only batched production exists, for demo purposes.
- [ ] Incident babysitting mode - when an application is blocked by high error ratio, then have a way to suspend everything but some select partition keys. Intended as a way to force through high priority events, hoping/betting that the errors would subside when the rest of the world is stopped.
- [ ] Incident readiness mode - visualize important keys (e.g. highlight errors in a special way if a high profile customer's stuff is stuck because of them)
- [ ] Tolerate hacks/poor man's workarounds - nondeterministic processes, unmanaged/expiring streams, force-skipped events, etc. - but visualize clearly in Comic Sans (also opt-outs from automatic optimization?) (also suboptimal ones that can't replay an instance within 5 minutes? unless it's Spīve's fault)
- [ ] Allow detaching and operating the application standalone. This works also as a "Plan B"/escape hatch/retreat path for early Spīve adopters
- [ ] Document/demo how to implement custom deploy/undeploy hooks listening to platform events to clean up application resources (like dashboards, or Pub/Sub subscriptions) reliably even if application instances die abruptly
- [ ] Example forking/decoration/piggyback flow - frictionlessly and safely reuse parent application(s) in-memory state as part of the model of a new application, replicated without the parent side effects, but with added I/O to more streams, and/or with different workloads
- [ ] SpiveCompactor as a separate infra application. (Only for compactable applications that provide the compaction job logic.) (Compactions can be batch, Kappa architecture meets Lambda architecture.) (Compaction also to play a role in GDPR/PII data deletion?) Compaction to generate historical graph? (Doubt)
- [ ] Example compaction job as a reduce operator in the same codebase as event handlers, implementing an additional interface? Example ad-hoc compaction job for debouncing flapping/reverting accidental flood of state changes?
- [ ] SpiveArchiver as a related infra application
- [ ] Investigate snapshotting the process memory at a low level... Though this wouldn't work across code versions... (No need to snapshot workloads or gateways, which deal with all the impure things like handles and resources. All the instance state in memory must be completely at rest between events. If we support spill to disk, must capture that too.) https://github.com/google/snappy-start, CRIU
- [ ] Support Observable state, to notify concurrent workloads when something changes? (Without breaking memory snapshotting?)
- [ ] Batching of side effects - how to batch also multiple side effects for a single partition while keeping consistency guarantees? Could be a relevant optimization in some use cases
- [ ] Fluid namespacing, decoupled from view grouping/favoriting (not displayed when unambiguous, trivial adding of a layer, assisted merging)... Coupled with ownership structure?
- [ ] Seamless rename refactor (trivial to move namespaces when ownership changes? One-commit rename of the application itself?)
- [ ] CRUDdy application helpers, with a state-keeping model (document model) defined abstractly and code-generated? When consuming multiple input streams, a document model joining them together can also be code-generated? Simplified side effect replay - only send the latest state of the document?
- [ ] Stream and event handler design should facilitate rekeying (e.g. `channel` -> `channel+priority` should not cause much code change, but enable queue management, if that's not at odds with the application logic. Same for `something` -> `something+s2cell` - not much code change, but enables geosharding)
- [ ] Grouping of companion applications which consume a given application's events (a compactor, a generated backup replicator, a generated optimization attempt, etc.) close together with the primary application for UI intuitiveness. (And for risky event visibility, and for cost visibility.)

### Undecided problems 

- [ ] Error pattern heuristics: noisy neighbors, clock drift, ...
- [ ] Collect at least basic resource saturation stats like CPU, IO, take into account to avoid noisy neighbors and misguided scaling decisions.
- [ ] Coordinate read rate and gateway backoff, to not have thundering herds when new cold instances join, and to not have overly sleepy herd either, whenever downstream recovery is sensed... Does this require a lot of inventory awareness? Should inventory formalize contention relations between gateways, so that on certain resource errors Spīve knows to back off the entire contended pool? (Generalized noisy neighbors)
