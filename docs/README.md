# Spīve

Event-sourcing and collaboration platform

  * ![lifecycle: pre-alpha](https://img.shields.io/badge/lifecycle-pre--alpha-a0c3d2.svg)

## Quick Start

TODO

Project is under construction. Build should succeed using on Linux at least:

    mvn verify

A basic "walking skeleton" can be launched in a `docker compose` environment:

    ./tools/dev-0/start.sh

Teaser trailer (not there yet):

![](screenshot.png "UI mockup showing event timelines of three applications. One of them colored to indicate warnings, and a cause in the form of an exception stacktrace can be easily identified.")

## Why

Spīve's primary goal is to boost productivity in software engineering, concretely - simplify development and management of event-driven applications, like microservices (such that may power your app features, websites or enterprise internal tools) and near-real time data processing pipelines.

A secondary goal is to build out a platform where business logic is neatly delineated from infrastructure concerns, such as event storage. This gives you greater power to carry out blanket optimizations across all your owned applications, thus achieving resource economy, as well as strategic capability to hop between vendors.

## How

Spīve, as a platform, implements a few relatively non-leaky abstractions for managing consistently ordered, partitioned streams of events, and orchestrates [event-sourced](https://www.cqrs.nu/Faq/event-sourcing) applications that reliably consume and produce such streams.

(Event-sourcing as a paradigm capitalizes on history retention, repeatable behaviors, and relies on in-memory data structures, to marry simple and easy development work with predictable processing performance. Spīve API is designed to directly assist application developers in successfully leveraging these principles.)

A few of the orchestration aspects include automated <abbr title="&quot;Keep The Lights On&quot;">KTLO<abbr> operations, such as instance restoration to healthy and live state by replaying historical events in case of crashes, assisted rollout of code changes, and proactive scaling (by sharding) in anticipation of excessive number of events hitting any single instance.

Spīve API does strive to abstract away storage and encoding of events - the application developer/owner should be enabled to work "in language" without extraneous concern about where technically the events came from and where they went. Spīve guarantees that the application code will keep executing in a consistent and timely<sup>TBD</sup> fashion to accept the incoming events, or otherwise the application owner will get alerted.

Under the hood, the Spīve abstractions unlock a slew of blanket optimizations that can be applied transparently. Examples include microbatching to trade off latency against throughput, moving the persisted event streams between differently priced storages, moving the computation between different machine types, etc.

Another upside is that the stored events are easily available for offline analytical processing in notebooks and batch pipelines, with minimal effort to extract the data.

## Limitations and tradeoffs

Spīve intends to be primarily opinionated towards development productivity within the "long tail" of computationally simple applications, and maintainability of such applications. (I.e. it is not yet another Big Data analytics engine with faster shuffle, or a better query language, or anything of that sort; though some Big Data use cases can also be expressed in event-sourcing model, especially if the problem is [embarrassingly parallel](https://en.wikipedia.org/wiki/Embarrassingly_parallel).)

Recognizing the significant engineering overhead present in this area, the design of Spīve platform readily trades off processor cycles in favor of maintainability and tight iteration cycles.

Spīve API makes different tradeoffs than those familiar to Beam or Hadoop users. Serializability of [event handler](API.md#Event_handlers) code to transfer it to distributed workers is not required - on the contrary, the application code (image, resp. jar) is deployed and executed as-built, directly on a set of virtual machine instances. Control of per-instance in-memory state using the native constructs of the programming language is facilitated, avoiding superfluous indirection. Event handling logic is designed to be single threaded: among simplicity benefits, it should be particularly noted that stacktraces in case of errors tend to be informative and pointing to the relevant places in business logic. Meanwhile the necessary parallelism for maintaining acceptable performance is achieved via partitioning of streams, resp. by sharding processes into multiple instances.

Complementing synchronous event handling, the framework embraces [workloads](API.md#Workloads) that run concurrently with event handlers. Workloads are suitable in practice for e.g. serving requests off the in-memory state with low latency and high availability.

Spīve ultimately provides safeguards so that out-of-order events are never observed by the business logic, which is of great help to easily reason about the correctness of application behavior. This needs to be reconciled with the real world circumstances where information may happen to arrive out-of-order though. Instead of exposing late arrival of events as a complicating condition in business logic, Spīve chooses to primarily address this problem through first class versioning of streams, resp. processes, along with convenience automation and orchestration to migrate the world (the dependency graph downstream of the corrupt stream, and their hitherto created side effects) to a corrected version of the event order. (Cf. n-temporal models, backdating.) This comes with recomputation costs that may be formidable yet feasible, a tradeoff for overall productivity gain.

## Usage

(TODO hello world example, development flow, operations flow)

See [API reference](API.md).

## Contributing

(TODO conventions and opinions)

Strongly opinionated that common development iteration UX should be blazingly fast, to not break [flow](https://en.wikipedia.org/wiki/Flow_state). Aspire to gamify common chores, such as deployment, scaling, and verification of code changes. Do precompute views, throw hardware at the problem, get UI to [update in split seconds](http://lawsofux.com/en/doherty-threshold/).

A [TODO list](TODO.md) full of wishes.

## Name

"Spīve" (/spiːve/) is a very underused Latvian word that means roughly "fierceness" or "incisiveness".
