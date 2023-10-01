A little automated analysis perhaps goes a long way towards feasible version storage and replay: check if it's better to replay a small subset of shards from scratch or to replay a small subset/ray of time from a snapshot. (Anything else than a ray would seem to be impossible in general, but maybe some ultra-advanced snapshots and contracts on the consumer...)

Need lots of education.

Some Docker-inspired slogan like "event sourcing for the masses"?

Also well suited for ML pipelines and model serving? Haven't tried

Also well suited for online game backends? Haven't tried

Make algorithms and data structures great again

Writing a smallish production system should not be much harder than completing a smallish programming tutorial.

Borne out of daily frustrations and repetitiveness in software engineer occupation, marred by all flavors of [CRUD](https://en.wikipedia.org/wiki/Create,_read,_update_and_delete) from RDBMS to NoSQL to NuSQL, mired in nightmares of prospects of death by a thousand YAML files.

Answer to the question: "What's the smallest amount of effort to get robust observability and scalability?" How to get there as cheaply as possible, with the least cognitive overhead.
* In my application code I would prefer to not write any retry loops or basic health metrics emitters ~ever again.
* Slack "monitoring" channels being capable of only showing ~5-10 happenings at once before you have to scroll - that is so much inferior compared to a <abbr title="&quot;Single pane of glass&quot;">SPOG</abbr> that can summarize thousands or millions of events in a couple square centimeters.
* Cut down on the number of IaC concepts involved, too. 100+ distinct AWS CF resources for a single application seems generally too much.

There are only two kinds of errors - hardware breaking, and bugs (programmer logic lapses, code reaching illegal/unanticipated state).

There is only one kind of warning - timeout/setback.
(We don't stop on warnings, we retry forever)
(Oops, what's OpaqueException then. Two kinds of warnings, timeout/overdue and gossip/mayday call?)
(Low level errors which are handled by retrying are not even errors. A series of them turns into a warning when retried for a certain amount of time.)

Quasi-durable execution? Is this structured scalability? (By analogy to structured concurrency)

S3 - Serviceless Structured Scalability? (Friends with D3 - Data Driven Documents)
S<SS

We taught sand to think, but then it got sidetracked with all those nondeterministic ideas... Sand, get back in line
Combinatorial implosion
Corky Cartwright - "Moore's law.. CPU clock frequencies stagnate.. Writing concurrent programs is difficult!"

Every low-volume stream - append BecomeIdle event with exponential backoff to ensure it's considered unchanged for collaboration?

Upfill - on discovering a buggy event or broken sequence, have automation to assist fixing simple problems in the inputs. (E.g. if just the event time should be different, perhaps the input event also should be just rewritten to a different event time, all the way up the lineage, without necessarily changing any of the processes in the platform?)

TOHACK Play any stream as a soundtrack, actually. Drums/sad trombones on errors, etc.

TOHACK git as a backend for "document editing" applications (and as an illustration of log-oriented architectures)

Duplicate side effects (or a series of them, exactly trailing another instance) is not a failure. Ensure the logging isn't confusing. (have a pseudo-log showing things as if this instance did them?)

Additional language support always with test helpers and a debug stack from the start, along with a productivity tutorial - otherwise no.

Is there a guardrail for preventing multiple processes from writing to one stream?

SpiveInventory ->
SpiveHermetizer?
SpiveHermeticInfra?
SpiveServices?
SpiveServiceProvisioningAndDiscovery?
SpiveRegistry?

SpiveScaler ->
SpiveTuner?
TuneCore?

In order to make the build and test cycle satisfyingly fast, we facilitate separate code repositories, and avoid requiring a monorepo.

Build-test-deploy cycle goal of ~15 minutes:
* some time for provisioning instances
* 5 min for replay on each instance, on average
* expect to stagger them a bit
* darkload canaries
* couple of minutes for zero downtime DNS swap

`Runner` (`RuntimeAgent`? `InstanceHost`? `Outstance`? `CapacityProvider`? `AppServer`?)

Highlight in ARCHITECTURE.md some coupling that is intentional in order to keep the mental model simple?
* Runners coupled with security domains
* Service discovery coupled with inventory (akin to deployment)
* Namespaces coupled with ownership domains (and redirection is maintained across ownership changes, similarly how GitHub orgs do it)
* Labels coupled with dashboards

EventStoreGateways are also different from other gateways because they detect when an instance is in catch-up and they report that to the platform. Generic gateways don't need to do that (thus we avoid extra constraints in ways of allowed side effects), but some of them might also report "already applied"?
What do we do when detecting catch-up from a gateway? Back off a few seconds (to prevent congestion), and try to reclaim lead once in a while, unless SpiveScaler chimes in with trailer leases? (This is work for the future.)
A workflow probably should not receive all gateways, but a facade of an EventStoreGateway, one that (helps synchronize and) may signal a conflict (up for app to wrap it in some retries on server side, or none). In event handlers, the gateway would just transparently progress or crash.

An application's output event log is always necessarily also its input? Otherwise, how to appendIfPrevTimeMatch() without a way to sync up with the times?
No, given that a stream is only written by one process, we probably can safely append() forwarding the event times from input(s)?

In gateway code, relying on client built-in retry features may make sense if they support indefinite retry, otherwise their use is likely redundant, as an infinite loop needs to be rolled around anyway.

Processes like neurons, reading 0-n streams and writing one. Optionally.

Streams keyed by whatever primary key you'd use in a database -> every join is a new process, new microservice.

    // The kind of sync that is needed for collaboration (to prevent a DeleteStream from happening
    // just before a CreateProcess happens to reference it)

    // The cheap kind of collaboration might not try to prevent it, but first append CreateProcess,
    // possibly breaking referential integrity, and then as soon as possible also an internal RPC to
    // advanceTimeIf() to the owning process of the referenced stream... and eventually succeed, or
    // otherwise emit DeleteProcess... And ensure none of this referencing happens in cycles...
    // The very cheap one is just to not support deletion. StreamManager still a separate app?

    // To support deletion of streams, a stream disappearing should cause a consequential
    // simultaneous DeleteProcess on any process using it... Still invalid references would exist
    // for a moment, so preferably the processes should get deleted first...
    //
    // The instance owning a stream would need to track all processes using it... Need to reify the
    // relations, can't have them merely be in the model?

  // Let every instance only output to one stream, but a process as a whole may have multiple
  // outputs which form a sort of a hierarchical schema.
  // Though what about workloads emitting events, can they emit mutations to all the streams while
  // staying oblivious to what their instance owns?
  // Since they all replicate the state machines of the subordinate streams, I guess they actually
  // can output to them, too... Though locking for verification needs to cover a stream and its
  // subordinates... How?
  // What about the distinct streams again - is the gateway still one and the same, or does the
  // application need to point out which stream to write? A bit rigid...

Locking covers an output gateway, so then we are actually set?
There is an infrastructure app ensuring that emitIf behaves similarly regardless if owned or unowned (via routing to the owning instance), and crashes if the actual stream is behind another application now (or infrastructure is ensuring that the new application cannot get deployed in the first place without stopping the existing one... How would that get enforced if they are owned by separate instances? Both need to be tracking the stream which changes ownership, and appending something on the stream's log? Sounds ok...)

(when trending towards capacity ceiling faster than a heuristic asymptote?)

This is not "no code" application building, this is very much "yes code", but the available I/O is generated and managed

if infrastructure simply let us process all changes to a group of related entities in eventTime order, just do a blocking "stop the world" computation of validity and only then process the subsequent changes... (Not sure how a collaborative validity app can work, watermark advance when?)

Traffic Director ..is not based on DNS

People say "service" but they have a function in mind (function of a request)... Stateful services can do the things we wish and still operate at web-scale (by sharding)

Serviceless = focus on data flow of each individual datum/field. DDD/OOP but without that much concern about how to slice it up into services.
