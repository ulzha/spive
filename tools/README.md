# Bootstrap

TODO documentation of the bootstrap process and the hardcoded bootstrap log (SpiveProdBootstrap.jsonl)

* prod-bootstrap runs only briefly during CI/CD (in the deployment action container) and then exits

  It would first ensure prod0 installation is wiped (aware of spive-prod0-basic-runner service hostnames), then wait until the sole SpiveProdBootstrap instance consumes the stream, and stop it right away.

  This does not enter replay mode (always carries out side effects). Between deployments we pretend that it just suffers a very long downtime. Being down, it never emits events, never watches the subsequent progress of SpiveProd0 instances in spive-prod0.

  Incidentally, this way we emulate an intentional total spive-prod0 crash on every CI/CD deployment.

  The higher-numbered installations are not crashed the same way, and are not redeployed on every CI/CD deployment; a long-running rolling upgrade to mitigate disruption needs to be triggered separately.

* prod-0 is undiscoverable and unsharded, nevertheless it is active-active replicated in 2 zones. SpiveProd0 runs as the sole application, only KTLO for a complete platform ecosystem around SpiveProd1
* prod-1 is undiscoverable (?) and sharded (forcibly? Not necessarily for performance reasons), and replicated in 2 zones x all regions. SpiveProd1 has a complete platform ecosystem around it - including DNS registerer-unregisterer, scaler, etc - and manages SpiveProd2
* prod-2 is therefore the first one that autoscales, and it has many nines availability. SpiveProd2 manages the control plane, so not only automated ecosystem tools but also platform developers interact with it every day, as well as feature developers, for whom UI serves prod-3 platform events (such as deployment logs, and autoscaling history) from here. They may also peer into the "backstage" to augment autoscaling rules, or deploy custom CI/CD and lifecycle management daemons specific to their applications
* prod-3 is the control plane. SpiveProd3 with the surrounding ecosystem is "the" Spive platform with which feature developers interact to run their applications

Aforementioned number of layers is somewhat arbitrary - an educated guess as to what might work well for a web scale enterprise. For small production setups, prod-1 or prod-2 could suffice as the control plane.

The buildup is a bit arbitrary too, meaning the manner in which feature set is expanded and scale is increased from layer to layer.

For doing development locally, you have the dev-*/start.sh tools to bootstrap test doubles of the production infrastructure (just prod-0 without replication, or other variously stripped-down and customized variants of the stack).

```
{CreateType {some non-core, like SpiveScaler, SpiveInventory}
{CreateGateway {some non-core, like BigtableEventStoreGateway, GkeRunnerGateway, PagerDutyGateway - so the bootstrap plane can alert in fancy ways}}
// ^ or unify those kinds of extension loading events in one mechanism? CreateType, CreateEventStore, CreateGateway
{CreateStream (durable event stores, fixed gids)}
// ^ the above types are perhaps not going to be in the same stream in prod, but they can be in bootstrap, and the code stays the same.
{CreateProcess {Spive, SpiveScaler, SpiveInventory}}
// ^ even processes are perhaps going to need a separate stream if we need to scale any prod process to million instances.
{CreateInstance {europe, north-america, asia}}
// At the limit, a Spive shard tracks a bunch of instances, their parent process, and the types, gateways, and streams that the process references... Owning the instances but not necessarily owning the process or any of the rest.
// The alternative would be breaking the app up into even smaller microservices, like SpiveStreamManager and SpiveTypeManager, where the productivity premise of a simple in-memory model gets torn down early...

// Is this a fused log, containing 3 streams? Overly complex?
```
