package io.ulzha.spive.app;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.linecorp.armeria.common.logging.LogLevel;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.logging.LoggingService;
import io.ulzha.spive.app.events.CreateEventLog;
import io.ulzha.spive.app.events.CreateInstance;
import io.ulzha.spive.app.events.CreateProcess;
import io.ulzha.spive.app.events.CreateStream;
import io.ulzha.spive.app.events.CreateType;
import io.ulzha.spive.app.events.DeleteInstance;
import io.ulzha.spive.app.events.DeleteProcess;
import io.ulzha.spive.app.events.InstanceIopw;
import io.ulzha.spive.app.events.InstanceProgress;
import io.ulzha.spive.app.events.InstanceStatusChange;
import io.ulzha.spive.app.lib.SpiveInstance;
import io.ulzha.spive.app.lib.SpiveOutputGateway;
import io.ulzha.spive.app.model.InstanceStatus;
import io.ulzha.spive.app.model.Platform;
import io.ulzha.spive.app.model.Process;
import io.ulzha.spive.app.model.Stream;
import io.ulzha.spive.app.model.Type;
import io.ulzha.spive.app.workloads.api.Cors;
import io.ulzha.spive.app.workloads.api.Rest;
import io.ulzha.spive.app.workloads.api.Sse;
import io.ulzha.spive.app.workloads.watchdog.WatchLoop;
import io.ulzha.spive.basicrunner.api.BasicRunnerGateway;
import io.ulzha.spive.basicrunner.api.RunThreadGroupRequest;
import io.ulzha.spive.basicrunner.api.ThreadGroupDescriptor;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.util.InterruptableSchedulable;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * Scalable, bootstrapped backend + API essentials for managing applications on the Spīve platform,
 * providing the minimum viable KTLO functionality, i.e. turning failed instances off and on again,
 * in the same availability zone.
 *
 * <p>("Turning an instance off and on again" occurs by deploying a new redundant instance first,
 * ensuring correct replay of its input events purely in memory, then turning on side effects, and
 * finally destroying the preexisting instance if it did not crash on its own, only if the new one
 * performs better.)
 *
 * <p>As with all Spīve applications, Spive event handlers may execute more than once. So we might
 * sometimes spuriously re-create and re-delete application instances after they have exited, unless
 * runners are idempotent. (For that they may need to keep durable history of instances, including
 * deleted ones.)
 */
public class Spive implements SpiveInstance {
  // move to the interface? whatever is cleaner
  // perhaps ProcessContext.output and ProcessContext.sideOutput a la Beam?
  // https://beam.apache.org/blog/2017/02/13/stateful-processing.html
  private final SpiveOutputGateway output;
  private final BasicRunnerGateway runner;

  private final Platform platform;

  public Spive(SpiveOutputGateway output, BasicRunnerGateway runner) {
    this.output = output;
    this.runner = runner;
    platform = new Platform("io.ulzha.dev");
  }

  @Override
  public void accept(final InstanceStatusChange event) {
    // TODO be a bit smart about runners disappearing en masse in the case of a network-wide event,
    // don't exhaust the available pool by reprovisioning all lost instances immediately, involve
    // humans in that case
    // Also don't upgrade OS for an entire deployment rapidly.
    // https://newsletter.pragmaticengineer.com/p/inside-the-datadog-outage
    // Have some sort of well-defined cap, and take care to prioritize upstreams in the DAG (will
    // necessitate some elaborate modeling)
    // TODO cleanup orphaned stuff, optional. Perhaps runners should do that

    Process.Instance instance = platform.getInstanceById(event.instanceId());
    InstanceStatus newStatus = InstanceStatus.valueOf(event.status());

    if (newStatus == instance.status) {
      throw new IllegalStateException("Repeated status");
    }
    instance.status = newStatus;

    switch (newStatus) {
      case ERROR:
        //        if (has a newer succeeding one) {
        //          // hrrmph, we don't have guarantee that the other one is in the same partition,
        // do we? Will have to check its status with RPCs in Watchdog?
        //            output.emit(new DeleteInstance());
        //        } else if (has no replacement attempt yet) {
        //            runnerUrl = runner.lookup(...);
        //            output.emit(new CreateInstance());
        //        }
        break;
      case TIMEOUT:
        // ... ~same
        break;
      case EXIT:
        // Perhaps not immediately, depending on config?
        output.emitIf(() -> true, new DeleteInstance(instance.id));
        break;
      default:
    }
  }

  @Override
  public void accept(final InstanceIopw event) {}

  @Override
  public void accept(final InstanceProgress event) {
    platform.getInstanceById(event.instanceId()).checkpoint = event.checkpoint();
  }

  @Override
  public void accept(final CreateEventLog event) {
    System.out.println("Accepting " + event + ", start: " + EventTime.fromString(event.start()));
  }

  @Override
  public void accept(final CreateType event) {
    System.out.println("Accepting " + event);
    platform.types.add(new Type());
  }

  @Override
  public void accept(final CreateStream event) {
    System.out.println("Accepting " + event);
    assert ("local".equals(event.eventStore())); // assert it's a class that exists?
    // TODO ensure (upon emitting) that name-version and id are not duplicated
    final Stream stream = new Stream(event.name(), event.streamId());
    platform.streamsById.put(stream.id, stream);
  }

  //  @Override
  //  public void accept(final CreateGateway event) {
  //    // RunnerGateway distinct for every pool? For PoC we only need fixed inventory
  //  }

  @Override
  public void accept(final CreateProcess event) {
    final Process process =
        new Process(
            event.name(),
            event.version(),
            event.processId(),
            event.artifactUrl(),
            event.availabilityZones(),
            event.inputStreamIds().stream()
                .map(platform.streamsById::get)
                .collect(Collectors.toSet()),
            event.outputStreamIds().stream()
                .map(platform.streamsById::get)
                .collect(Collectors.toSet()));
    platform.processesById.put(process.id, process);
    platform
        .processesByApplicationAndVersion
        .computeIfAbsent(process.name, name -> new HashMap<>())
        .put(process.version, process);
    // TODO ensure (upon emitting) that name-version and id are not duplicated
    // TODO ensure (upon emitting) that the output stream exists
    // TODO ensure (upon emitting) that there are no cycles of streams
    // TODO decide initial sharding and emit CreateInstance events...
    // TODO validate artifact (upon emitting? async?) - ensure that all the event handlers are
    // implemented, etc.
    // TODO ensure its consistency is validated to some extent in each runner internally as well
    platform.processesEtag = process.id.toString();
    // TODO lock-free
    synchronized (platform) {
      platform.notifyAll();
    }
  }

  @Override
  public void accept(final CreateInstance event) {
    final Process process = platform.processesById.get(event.processId());
    final Process.Instance newInstance =
        new Process.Instance(
            event.instanceId(),
            process,
            URI.create(event.runnerUrl()).resolve("/" + event.instanceId()));
    process.instances.add(newInstance);
    platform.instancesById.put(event.instanceId(), newInstance);

    // FIXME superfluous? Prefer one canonical way for launching a jar, one for Docker image, etc?
    final String[] parts = process.artifactUrl.split(";mainClass=");
    final RunThreadGroupRequest request =
        new RunThreadGroupRequest(
            new ThreadGroupDescriptor(
                event.instanceId().toString(),
                parts[0],
                parts[1],
                List.of(
                    "io.ulzha.spive.core.BigtableEventStore;projectId=user-dev;instanceId=spive-dev-0;hostname=bigtable-emulator;port=8086",
                    "2c543574-f3ac-4b4c-8a5b-a5e188b9bc94",
                    "io.ulzha.spive.core.BigtableEventStore;projectId=user-dev;instanceId=spive-dev-0;hostname=bigtable-emulator;port=8086",
                    "2c543574-f3ac-4b4c-8a5b-a5e188b9bc94",
                    "dev-1",
                    "*")));

    runner.startInstance(request, event.runnerUrl());
  }

  @Override
  public void accept(final DeleteInstance event) {
    Process.Instance instance = platform.instancesById.remove(event.instanceId());
    instance.process.instances.remove(instance);
    instance.process = null;
    // runner.stopInstance(...);
    // for idempotency, the runner perhaps can enumerate all instances it has running, and count
    // absence as success
  }

  @Override
  public void accept(final DeleteProcess event) {
    final Process process = platform.processesById.get(event.processId());
    platform.processesById.remove(process.id);
    platform.processesByApplicationAndVersion.get(process.name).remove(process.version);
    if (platform.processesByApplicationAndVersion.get(process.name).isEmpty()) {
      platform.processesByApplicationAndVersion.remove(process.name);
    }
    platform.processesEtag = "-" + process.id.toString();
    synchronized (platform) {
      platform.notifyAll();
    }
  }

  // TODO not start this one until caught up
  public class Watchdog implements Runnable {
    // ephemeral state in workloads is ok, so long as we are ok with it getting emptied on redeploy
    private final ScheduledExecutorService watchExecutor = Executors.newScheduledThreadPool(1);
    private final WatchLoop watchLoop = new WatchLoop(platform, output);

    @Override
    public void run() {
      try {
        watchExecutor
            .scheduleAtFixedRate(new InterruptableSchedulable(watchLoop::watchOnce), 0, 10, SECONDS)
            .get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public class Api implements Runnable {
    @Override
    public void run() {
      // FIXME port determined from the control plane or random via runner decision?
      final Server server =
          Server.builder()
              .http(8440)
              .annotatedService("/api", new Rest(platform, output))
              .service("/sse", Sse.service(platform))
              .decorator(Cors.decorator())
              .decorator(
                  LoggingService.builder()
                      .requestLogLevel(LogLevel.INFO)
                      .successfulResponseLogLevel(LogLevel.INFO)
                      .failureResponseLogLevel(LogLevel.INFO)
                      .newDecorator())
              .build();
      server.closeOnJvmShutdown(() -> System.out.println("Server closing on JVM shutdown"));
      server.start().join();

      try {
        server.blockUntilShutdown();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
  }
}
