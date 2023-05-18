package io.ulzha.spive.threadrunner;

import com.spotify.apollo.Request;
import com.spotify.apollo.Response;
import com.spotify.apollo.Status;
import com.spotify.apollo.route.AsyncHandler;
import com.spotify.apollo.route.Middlewares;
import com.spotify.apollo.route.Route;
import com.spotify.apollo.route.RouteProvider;
import io.ulzha.spive.threadrunner.api.GetThreadGroupHeartbeatResponse;
import io.ulzha.spive.threadrunner.api.GetThreadGroupsResponse;
import io.ulzha.spive.threadrunner.api.RunThreadGroupRequest;
import io.ulzha.spive.threadrunner.api.ThreadGroupDescriptor;
import io.ulzha.spive.threadrunner.api.Umbilical;
import io.ulzha.spive.threadrunner.util.Jars;
import io.ulzha.spive.threadrunner.util.Json;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbException;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs and tracks Spīve applications, a thread group per process instance, inside the current JVM.
 *
 * <p>The requested jar is downloaded over network, and its Spive-generated inner Main.main() is
 * executed. An Umbilical object is shared with the running code and serves as a channel for
 * progress reporting.
 *
 * <p>This is a naive implementation prone to classloading problems, yet very lightweight, meant to
 * be useful for local development and for basic proof-of-concept operations.
 *
 * <p>TODO spive-kubernetes-runner using StatefulSet? Utilizing minikube for local development?
 */
class ThreadRunnerResource implements RouteProvider {
  private static final Logger LOG = LoggerFactory.getLogger(ThreadRunnerResource.class);
  private final Jsonb jsonb = Json.create();
  private final Map<String, ThreadGroupRecord> records = new ConcurrentHashMap<>();

  @Override
  public Stream<Route<AsyncHandler<Response<ByteString>>>> routes() {
    Stream<Route<AsyncHandler<Response<ByteString>>>> routes =
        Stream.of(
            Route.sync("PUT", "/thread_groups", context -> runThreadGroup(context.request()))
                .withMiddleware(ThreadRunnerResource::serialize)
                .withDocString(
                    "Creates a new thread group and in it runs the provided main class with the provided args",
                    "Includes last checkpointed event time, up till which to replay without side effects"),
            Route.sync(
                    "PATCH",
                    "/thread_groups/<name>",
                    context ->
                        fwdThreadGroup(
                            context.pathArgs().get("name"), context.request().payload().toString()))
                .withMiddleware(ThreadRunnerResource::serialize)
                .withDocString(
                    "Controls replay mode",
                    "Updates last checkpointed event time, up till which to replay without side effects. Alternatively can also schedule stopping of side effects again at a certain event time in the future - one example where it could be useful is performing a best effort graceful handover to a new instance."),
            Route.sync("GET", "/thread_groups", context -> getThreadGroups())
                .withMiddleware(ThreadRunnerResource::serialize)
                .withDocString(
                    "Lists the currently running thread groups",
                    "Includes metadata about who deployed them."),
            Route.sync(
                    "DELETE",
                    "/thread_groups/<name>",
                    context -> deleteThreadGroup(context.pathArgs().get("name")))
                .withMiddleware(ThreadRunnerResource::serialize)
                .withDocString("Interrupts (forcibly stops) a thread group by name", ""),
            // The following endpoint provides a best-effort way of error signaling from the
            // event
            // handlers and workloads, with very limited retention and throughput (to
            // prioritize
            // business logic).
            // The built in error signaling probably should not be developed further than a
            // basic
            // enabler of KTLO and autoscaling, with minimal dependencies, and an enabler of
            // extremely fast and adequately structured error information propagation to UI
            // in a few
            // most common troubleshooting scenarios.
            // Production environments would typically additionally log to a commodity async
            // logging
            // stack to retain larger volume of debug information, do ad-hoc analysis, etc.
            Route.sync(
                    "GET",
                    "/thread_groups/<name>/heartbeat",
                    context -> getThreadGroupHeartbeat(context.pathArgs().get("name")))
                .withMiddleware(ThreadRunnerResource::serialize)
                .withDocString(
                    "Returns latest progress and failure information for one thread group",
                    "Serves the minimum information necessary for KTLO. This includes first error and first warning (if any) with stacktraces, plus start and end time of the latest event handler executed."));
    // TODO a separate endpoint with increased verbosity, to analyze for scaling
    // purposes?
    // TODO a dump endpoint for seeing thread tree, and classloaders with their
    // loaded classes

    return routes.map(r -> r.withPrefix("/api/v0").withMiddleware(Middlewares.apolloDefaults()));
  }

  private static AsyncHandler<Response<ByteString>> serialize(
      final AsyncHandler<Response<String>> handler) {
    return requestContext ->
        handler
            .invoke(requestContext)
            .thenApply(
                unserialized ->
                    unserialized.withPayload(
                        unserialized.payload().map(ByteString::encodeUtf8).orElse(null)));
  }

  private static class ThreadGroupRecord {

    private ThreadGroupRecord(
        final ThreadGroup threadGroup, final ThreadGroupDescriptor threadGroupDescriptor) {
      this.threadGroup = threadGroup;
      this.threadGroupDescriptor = threadGroupDescriptor;
      this.umbilical = new Umbilical();
    }

    ThreadGroup threadGroup;
    ThreadGroupDescriptor threadGroupDescriptor;
    Umbilical umbilical;
  }

  /**
   * Creates a new thread group and in it runs the provided main class with the provided args.
   *
   * @param rawRequest
   */
  private Response<String> runThreadGroup(final Request rawRequest) {
    if (rawRequest.payload().isEmpty()) {
      LOG.info("Bad request: " + rawRequest);
      return Response.forStatus(Status.BAD_REQUEST).withPayload("Empty payload");
    }
    final String requestJson = rawRequest.payload().get().utf8();
    final RunThreadGroupRequest request;
    try {
      request = jsonb.fromJson(requestJson, RunThreadGroupRequest.class);
    } catch (JsonbException e) {
      LOG.info("Bad request: " + requestJson, e);
      return Response.forStatus(Status.BAD_REQUEST).withPayload(e.getMessage());
    }

    final ThreadGroup threadGroup = new ThreadGroup(request.threadGroup.name);
    final ThreadGroupRecord record = new ThreadGroupRecord(threadGroup, request.threadGroup);

    if (records.putIfAbsent(request.threadGroup.name, record) == null) {
      final Thread thread =
          new Thread(threadGroup, () -> runMain(record), request.threadGroup.name + "-main");
      // dunno if we need an uncaught exception handler, things are caught alright by
      // runMain

      // TODO not start arbitrarily many on a single machine
      thread.start();

      // TODO with deterministic umbilicalUri
      return Response.forStatus(Status.ACCEPTED)
          .withPayload(
              "http://somethingsomething/api/v0/thread_groups/"
                  + request.threadGroup.name
                  + "/heartbeat");
    } else {
      // already exists
      return Response.forStatus(Status.CONFLICT);
    }
  }

  private static void runMain(final ThreadGroupRecord record) {
    final ThreadGroupDescriptor descriptor = record.threadGroupDescriptor;
    try {
      final File jarFile;
      jarFile = Jars.getJar(descriptor.artifactUrl);
      final String[] args = descriptor.args.toArray(new String[0]);
      Jars.runJar(jarFile, descriptor.mainClass, "main", record.umbilical, args);
    } catch (Exception e) {
      LOG.error(
          "Caught exception in "
              + Thread.currentThread()
              + " running "
              + descriptor
              + " - exiting abnormally",
          e);
      record.umbilical.addError(null, e);
    }
    // Long running requests ok? Or will we have STARTING state? Rather NOMINAL
    // while
    // awaiting workload heartbeat, subject to a runner-specific startup timeout?
  }

  private Response<String> fwdThreadGroup(final String name, final String requestJson) {
    return Response.forStatus(Status.NOT_IMPLEMENTED);
  }

  /** Lists the currently running thread groups. */
  private Response<String> getThreadGroups() {
    GetThreadGroupsResponse threadGroups =
        GetThreadGroupsResponse.create(
            records.values().stream()
                .map(record -> record.threadGroupDescriptor)
                .collect(Collectors.toList()));
    return Response.forPayload(jsonb.toJson(threadGroups));
  }

  /**
   * Interrupts (forcibly stops) a thread group by name.
   *
   * <p>The thread group is not fully guaranteed to get stopped, but it will not be returned from
   * the API in subsequent calls. TODO block until the thread is not found any more? Or force via OS
   */
  private Response<String> deleteThreadGroup(final String name) {
    final ThreadGroupRecord record = records.get(name);

    if (record == null) {
      return Response.forStatus(Status.NOT_FOUND);
    } else {
      record.threadGroup.interrupt();
      records.remove(name);

      return Response.forStatus(Status.ACCEPTED);
    }
  }

  /** Returns progress and failure information for one thread group. */
  private Response<String> getThreadGroupHeartbeat(final String name) {
    final ThreadGroupRecord record = records.get(name);

    if (record == null) {
      return Response.forStatus(Status.NOT_FOUND);
    }

    final GetThreadGroupHeartbeatResponse threadGroupHeartbeat =
        GetThreadGroupHeartbeatResponse.create(record.umbilical);
    return Response.forStatus(Status.OK).withPayload(jsonb.toJson(threadGroupHeartbeat));
  }
}
