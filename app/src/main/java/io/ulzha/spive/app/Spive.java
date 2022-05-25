package io.ulzha.spive.app;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.ulzha.spive.app.events.CreateEventLog;
import io.ulzha.spive.app.events.CreateEventSchema;
import io.ulzha.spive.app.events.CreateInstance;
import io.ulzha.spive.app.events.CreateProcess;
import io.ulzha.spive.app.events.CreateStream;
import io.ulzha.spive.app.events.DeleteInstance;
import io.ulzha.spive.app.events.DeleteProcess;
import io.ulzha.spive.app.events.InstanceProgress;
import io.ulzha.spive.app.events.InstanceStatusChange;
import io.ulzha.spive.app.lib.SpiveInstance;
import io.ulzha.spive.app.lib.SpiveOutputGateway;
import io.ulzha.spive.app.model.EventSchema;
import io.ulzha.spive.app.model.InstanceStatus;
import io.ulzha.spive.app.model.Platform;
import io.ulzha.spive.app.model.Process;
import io.ulzha.spive.app.model.Stream;
import io.ulzha.spive.app.workloads.frontend.Processes;
import io.ulzha.spive.app.workloads.watchdog.WatchLoop;
import io.ulzha.spive.threadrunner.api.RunThreadGroupRequest;
import io.ulzha.spive.threadrunner.api.ThreadGroupDescriptor;
import io.ulzha.spive.threadrunner.api.ThreadRunnerGateway;
import io.ulzha.spive.util.InterruptableSchedulable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Provides the basic backend, web UI and API for managing applications on the Spīve platform,
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
  private final ThreadRunnerGateway runner;

  private final Platform platform;

  public Spive(SpiveOutputGateway output, ThreadRunnerGateway runner) {
    this.output = output;
    this.runner = runner;
    platform = new Platform("io.ulzha.dev");
  }

  @Override
  public void accept(final InstanceStatusChange event) {
    // TODO be a bit smart about runners disappearing en masse in the case of a network-wide event,
    // don't exhaust the available pool by reprovisioning all lost instances immediately, involve
    // humans in that case
    // TODO cleanup orphaned stuff, optional. Perhaps runners should do that

    Process.Instance instance = platform.getInstanceById(event.instanceId);
    InstanceStatus newStatus = InstanceStatus.valueOf(event.status);

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
        output.emitIf(() -> true, new DeleteInstance());
        break;
      default:
    }
  }

  @Override
  public void accept(final InstanceProgress event) {
    platform.getInstanceById(event.instanceId).checkpoint = event.checkpoint;
  }

  @Override
  public void accept(final CreateEventLog event) {
    System.out.println("Accepting " + event);
  }

  @Override
  public void accept(final CreateEventSchema event) {
    System.out.println("Accepting " + event);
    platform.eventSchemas.add(new EventSchema());
  }

  @Override
  public void accept(final CreateStream event) {
    System.out.println("Accepting " + event);
    assert ("local".equals(event.eventStore)); // assert it's a class that exists?
    // TODO ensure (upon emitting) that name-version and id are not duplicated
    platform.streams.add(new Stream(event.name));
  }

  //  @Override
  //  public void accept(final CreateGateway event) {
  //    // RunnerGateway distinct for every pool? For PoC we only need fixed inventory
  //  }

  @Override
  public void accept(final CreateProcess event) {
    Process newProcess = new Process(event.artifact);
    newProcess.name = event.name;
    newProcess.id = event.processId;
    newProcess.availabilityZones = event.availabilityZones;
    platform.processesById.put(event.processId, newProcess);
    // TODO ensure (upon emitting) that name-version and id are not duplicated
    // TODO ensure (upon emitting) that the output stream exists
    // TODO decide initial sharding and emit CreateInstance events... 1000 as a sensible default?
    // TODO validate artifact (upon emitting? async?) - ensure that all the event handlers are
    // implemented, etc.
    // TODO ensure its consistency is validated to some extent in each runner internally as well
  }

  @Override
  public void accept(final CreateInstance event) {
    final Process process = platform.processesById.get(event.processId);
    final Process.Instance newInstance = new Process.Instance(event.instanceId, process);
    process.instances.add(newInstance);
    platform.instancesById.put(event.instanceId, newInstance);

    final RunThreadGroupRequest request = new RunThreadGroupRequest();
    request.threadGroup = new ThreadGroupDescriptor();
    request.threadGroup.name = event.instanceId.toString();
    // FIXME superfluous? Prefer one canonical way for launching a jar, one for Docker image, etc?
    final String[] parts = process.artifact.split(";mainClass=");
    request.threadGroup.artifactUrl = parts[0];
    request.threadGroup.mainClass = parts[1];
    // FIXME
    request.threadGroup.args =
        List.of(
            "io.ulzha.spive.core.BigtableEventStore;projectId=user-dev;instanceId=spive-dev-0;hostname=bigtable-emulator;port=8086",
            "2c543574-f3ac-4b4c-8a5b-a5e188b9bc94",
            "io.ulzha.spive.core.BigtableEventStore;projectId=user-dev;instanceId=spive-dev-0;hostname=bigtable-emulator;port=8086",
            "2c543574-f3ac-4b4c-8a5b-a5e188b9bc94",
            "dev-1",
            "*");

    runner.startInstance(request, event.runnerUrl);
  }

  @Override
  public void accept(final DeleteInstance event) {
    Process.Instance instance = platform.instancesById.remove(event.instanceId);
    instance.process.instances.remove(instance);
    // runner.stopInstance(...);
  }

  @Override
  public void accept(final DeleteProcess event) {
    platform.processesById.remove(event.processId);
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

  public class Frontend implements Runnable {
    @Override
    public void run() {
      final HttpServer httpServer;

      try {
        // FIXME port determined from the control plane or random via runner decision?
        httpServer = HttpServer.create(new InetSocketAddress(8040), 10);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      final ExecutorService executorService = Executors.newCachedThreadPool();
      httpServer.setExecutor(executorService);
      httpServer.createContext("/", new RootHandler());
      httpServer.createContext("/api", new ApiHandler());
      httpServer.createContext("/static", new StaticHandler());
      httpServer.start();
      // FIXME use something (Jetty?) that allows to monitor or join the server thread
      try {
        while (true) {
          Thread.sleep(1000 * 3600 * 24);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      } finally {
        httpServer.stop(1);
      }
    }

    private class RootHandler implements HttpHandler {
      public void handle(HttpExchange exchange) throws IOException {
        System.out.println(
            "RootHandler " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
        exchange.sendResponseHeaders(404, 0);
        try (OutputStream body = exchange.getResponseBody()) {
          body.write("<h1>404 Not Found</h1>Off by a wide margin".getBytes(StandardCharsets.UTF_8));
        }
      }
    }

    private class StaticHandler implements HttpHandler {
      public void handle(HttpExchange exchange) throws IOException {
        System.out.println(
            "StaticHandler " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
        URI uri = exchange.getRequestURI();
        final InputStream is =
            Spive.class.getClassLoader().getResourceAsStream(uri.getPath().substring(1));
        if (is == null) {
          exchange.sendResponseHeaders(404, 0);
          try (OutputStream body = exchange.getResponseBody()) {
            body.write("<h1>404 Not Found</h1>Resource not found".getBytes(StandardCharsets.UTF_8));
          }
        } else {
          try {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream body = exchange.getResponseBody()) {
              is.transferTo(body);
            }
          } finally {
            is.close();
          }
        }
      }
    }

    private class ApiHandler implements HttpHandler {
      private final ObjectMapper objectMapper = new ObjectMapper();
      private final Processes processes = new Processes(platform);

      public void handle(HttpExchange exchange) throws IOException {
        System.out.println(
            "ApiHandler " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
        final String method = exchange.getRequestMethod();
        final String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/applications") && method.equals("GET")) {
          exchange.sendResponseHeaders(200, 0);
          try (OutputStream body = exchange.getResponseBody()) {
            // List of all applications (on this instance), for displaying on a dashboard.
            String applicationsJson = objectMapper.writeValueAsString(processes.list());
            body.write(applicationsJson.getBytes(StandardCharsets.UTF_8));
          }
        } else if (path.equals("/api/applications") && method.equals("PUT")) {
          exchange.sendResponseHeaders(200, 0);
          try (OutputStream body = exchange.getResponseBody()) {
            // TODO prevent duplicate creation events by design...
            //  Does that require generation of GUIDs here in handlers?

            // emitRandom
            // emitSpontaneous
            if (output.emitIf(() -> true, new CreateEventSchema())) {
              body.write("OK".getBytes(StandardCharsets.UTF_8));
            } else {
              body.write("Not ok, retry yourself".getBytes(StandardCharsets.UTF_8));
            }
          }
        } else {
          exchange.sendResponseHeaders(404, 0);
          try (OutputStream body = exchange.getResponseBody()) {
            body.write("KRAKEN".getBytes(StandardCharsets.UTF_8));
          }
        }
      }
    }
  }
}
