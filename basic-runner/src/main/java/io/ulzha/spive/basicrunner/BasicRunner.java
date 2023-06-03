package io.ulzha.spive.basicrunner;

import com.sun.net.httpserver.HttpExchange;
import io.ulzha.spive.basicrunner.api.GetThreadGroupHeartbeatResponse;
import io.ulzha.spive.basicrunner.api.GetThreadGroupsResponse;
import io.ulzha.spive.basicrunner.api.RunThreadGroupRequest;
import io.ulzha.spive.basicrunner.api.ThreadGroupDescriptor;
import io.ulzha.spive.basicrunner.api.Umbilical;
import io.ulzha.spive.basicrunner.util.Http;
import io.ulzha.spive.basicrunner.util.Jars;
import io.ulzha.spive.basicrunner.util.Rest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs and tracks Spīve applications, a thread group per process instance, multitenant in one and
 * the same JVM.
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
public final class BasicRunner {
  private static final Logger LOG = LoggerFactory.getLogger(BasicRunner.class);
  private static final Map<String, ThreadGroupRecord> RECORDS = new ConcurrentHashMap<>();

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

  public static void main(final String... args) {
    Http.startServer(
        8430,
        Rest.handler(
            Rest.get("^/ping$", Http.jsonHandler(BasicRunner::ping, String.class)),
            Rest.post(
                "^/api/v0/thread_groups$",
                Http.jsonHandler(
                    BasicRunner::runThreadGroup, RunThreadGroupRequest.class, String.class)),
            Rest.patch(
                "^/api/v0/thread_groups/(?<name>[^/]*)$",
                Http.jsonHandler(BasicRunner::signalThreadGroup, String.class)),
            Rest.get(
                "^/api/v0/thread_groups$",
                Http.jsonHandler(BasicRunner::getThreadGroups, GetThreadGroupsResponse.class)),
            Rest.get(
                "^/api/v0/thread_groups/(?<name>[^/]*)/heartbeat$",
                Http.jsonHandler(
                    BasicRunner::getThreadGroupHeartbeat, GetThreadGroupHeartbeatResponse.class)),
            Rest.delete(
                "^/api/v0/thread_groups/(?<name>[^/]*)$",
                Http.jsonHandler(BasicRunner::deleteThreadGroup, String.class))));

    // FIXME uncaught handler... And/or how to join the background thread to confirm that it really
    // started
    // Maybe also mark all umbilicals with warnings if such shenanigans occur
    LOG.info("Runner started");
  }

  /** Healthcheck endpoint. */
  private static String ping(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(200, 0);
    return "pong";
  }

  /**
   * Creates a new thread group and in it runs the provided main class with the provided args.
   *
   * <p>TODO The request includes last EventTime up till which to replay without side effects.
   */
  private static String runThreadGroup(HttpExchange exchange, RunThreadGroupRequest request)
      throws IOException {
    final ThreadGroup threadGroup = new ThreadGroup(request.threadGroup().name());
    final ThreadGroupRecord record = new ThreadGroupRecord(threadGroup, request.threadGroup());

    if (RECORDS.putIfAbsent(request.threadGroup().name(), record) == null) {
      final Thread thread =
          new Thread(threadGroup, () -> runMain(record), request.threadGroup().name() + "-main");
      // dunno if we need an uncaught exception handler, things are caught alright by runMain

      // TODO not start arbitrarily many on a single machine
      thread.start();

      // TODO with deterministic umbilicalUri
      exchange.sendResponseHeaders(201, 0);
      return "http://somethingsomething/api/v0/thread_groups/"
          + request.threadGroup().name()
          + "/heartbeat";
    } else {
      // already exists
      exchange.sendResponseHeaders(409, 0);
      return "";
    }
  }

  private static void runMain(final ThreadGroupRecord record) {
    final ThreadGroupDescriptor descriptor = record.threadGroupDescriptor;
    try {
      Jars.runJar(
          Jars.getJar(descriptor.artifactUrl()),
          descriptor.mainClass(),
          "main",
          record.umbilical,
          descriptor.args().toArray(new String[0]));
    } catch (Exception e) {
      LOG.warn("Failed to run " + descriptor + " - reporting " + e.getMessage());
      record.umbilical.addError(null, e);
      throw new RuntimeException(e);
    }
    // Long running requests ok? Or will we have STARTING state? Rather NOMINAL while
    // awaiting workload heartbeat, subject to a runner-specific startup timeout?
  }

  /**
   * Controls replay mode
   *
   * <p>Updates last checkpointed event time, up till which to replay without side effects.
   * Alternatively can also schedule stopping of side effects again at a certain event time in the
   * future - one example where it could be useful is performing a best effort graceful handover to
   * a new instance.
   */
  private static String signalThreadGroup(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(501, 0);
    return "";
  }

  /**
   * Lists all currently running thread groups.
   *
   * <p>TODO Includes metadata about who deployed them.
   */
  private static GetThreadGroupsResponse getThreadGroups(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(200, 0);
    return new GetThreadGroupsResponse(
        RECORDS.values().stream()
            .map(record -> record.threadGroupDescriptor)
            .collect(Collectors.toList()));
  }

  /**
   * Interrupts (forcibly stops) a thread group by name.
   *
   * <p>The thread group is not fully guaranteed to get stopped, but it will not be returned from
   * the API in subsequent calls. TODO block until the thread is not found any more? Or force via OS
   */
  private static String deleteThreadGroup(HttpExchange exchange) throws IOException {
    final ThreadGroupRecord record = RECORDS.get(Rest.pathParam(exchange, "name"));

    if (record == null) {
      exchange.sendResponseHeaders(404, 0);
    } else {
      record.threadGroup.interrupt();
      RECORDS.remove(Rest.pathParam(exchange, "name"));

      exchange.sendResponseHeaders(204, 0);
    }
    return "";
  }

  /**
   * Returns latest progress and failure information for one thread group.
   *
   * <p>This built-in error signaling is an enabler of rapid error information propagation to Spive
   * UI, geared towards automatic KTLO as well as some extremely common manual troubleshooting
   * scenarios.
   *
   * <p>Information is structured and includes
   *
   * <ul>
   *   <li>first error and first warning (if any) with stacktraces,
   *   <li>sample of start timings and progress timings of the latest event handlers executed,
   *   <li>checkpoint, the EventTime of the latest event successfully handled.
   * </ul>
   *
   * <p>Production applications would typically additionally log to a commodity asynchronous
   * observability stack to retain extensive amount of debug information, generate metrics, do
   * ad-hoc analysis, etc.
   */
  private static GetThreadGroupHeartbeatResponse getThreadGroupHeartbeat(HttpExchange exchange)
      throws IOException {
    final ThreadGroupRecord record = RECORDS.get(Rest.pathParam(exchange, "name"));
    if (record == null) {
      exchange.sendResponseHeaders(404, 0);
      return new GetThreadGroupHeartbeatResponse(List.of(), null);
    }
    exchange.sendResponseHeaders(200, 0);
    return GetThreadGroupHeartbeatResponse.create(record.umbilical);
  }
}
