package io.ulzha.spive.basicrunner;

import com.sun.net.httpserver.HttpExchange;
import io.ulzha.spive.basicrunner.api.GetThreadGroupHeartbeatResponse;
import io.ulzha.spive.basicrunner.api.GetThreadGroupsResponse;
import io.ulzha.spive.basicrunner.api.RunThreadGroupRequest;
import io.ulzha.spive.basicrunner.api.ThreadGroupDescriptor;
import io.ulzha.spive.basicrunner.api.Umbilical;
import io.ulzha.spive.basicrunner.util.Http;
import io.ulzha.spive.basicrunner.util.Http.StatusCode;
import io.ulzha.spive.basicrunner.util.Jars;
import io.ulzha.spive.basicrunner.util.Rest;
import io.ulzha.spive.lib.HandledException;
import io.ulzha.spive.lib.OpaqueException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    Thread.setDefaultUncaughtExceptionHandler(new RootExceptionHandler());

    Http.startServer(
        8430,
        Rest.handler(
            Http.fourOhFourHandler(),
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

    // TODO Join the background thread to confirm that it really started? Just for truthful logging.
    // Otherwise might be unnecessary - Gateways talking to runners must be prepared to check health
    // and disappearance anyway.
    LOG.info("Runner started");
    // sleep until interrupted?
  }

  /**
   * Ensures that we mark all affected umbilicals with warnings, for visibility.
   *
   * <p>Also does singular consistent logging for all errors that propagate. (LOG.error with
   * stacktrace is likely redundant to do anywhere else)
   */
  private static class RootExceptionHandler implements UncaughtExceptionHandler {
    private String addWarnings(Thread thread, Throwable t) {
      for (ThreadGroup tg = thread.getThreadGroup(); tg != null; tg = tg.getParent()) {
        final ThreadGroupRecord record = RECORDS.get(tg.getName());
        if (record != null) {
          // FIXME collisions can occur; check that tg's parent is main
          // mark the particular application's umbilical with a warning
          // interruption by platform ecosystem should bubble up here, and not be opaque TODO test
          record.umbilical.addWarning(null, t);
          return "Uncaught exception from {} belonging to "
              + record.threadGroupDescriptor.toString();
        }
      }
      // might be relevant to any application on this runner, but we shouldn't disclose internals
      // across applications
      for (ThreadGroupRecord record : RECORDS.values()) {
        record.umbilical.addWarning(null, new OpaqueException(t));
      }
      return "Uncaught exception from {} - runner might be unstable";
    }

    @Override
    public void uncaughtException(Thread thread, Throwable t) {
      final String message;
      if (t instanceof HandledException) {
        // specific feedback must've already been sent as a best effort
        message = "Propagated from {}";
      } else {
        message = addWarnings(thread, t);
      }
      LOG.error(message, thread, t);
    }
  }

  /** Healthcheck endpoint. */
  private static Http.Response<String> ping(HttpExchange exchange) {
    return Http.response(StatusCode.OK, "pong");
  }

  /**
   * Creates a new thread group and in it runs the provided main class with the provided args.
   *
   * <p>TODO The request includes last EventTime up till which to replay without side effects.
   */
  private static Http.Response<String> runThreadGroup(
      HttpExchange exchange, RunThreadGroupRequest request) {
    final String name = request.threadGroup().name();
    final ThreadGroup threadGroup = new ThreadGroup(name);
    final ThreadGroupRecord record = new ThreadGroupRecord(threadGroup, request.threadGroup());

    if (RECORDS.putIfAbsent(name, record) == null) {
      final Thread thread = new Thread(threadGroup, () -> runMain(record), "basic-runner-" + name);

      // TODO not start arbitrarily many on a single machine
      thread.start();

      // TODO with deterministic umbilicalUri
      return Http.response(
          StatusCode.ACCEPTED,
          "http://somethingsomething/api/v0/thread_groups/%s/heartbeat"
              .formatted(URLEncoder.encode(name, StandardCharsets.UTF_8)));
    } else {
      // already exists
      return Http.response(StatusCode.CONFLICT);
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
      throw new HandledException(e);
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
  private static Http.Response<String> signalThreadGroup(HttpExchange exchange) {
    return Http.response(StatusCode.NOT_IMPLEMENTED);
  }

  /**
   * Lists all currently running thread groups.
   *
   * <p>TODO Includes metadata about who deployed them.
   */
  private static Http.Response<GetThreadGroupsResponse> getThreadGroups(HttpExchange exchange) {
    return Http.response(
        StatusCode.OK,
        new GetThreadGroupsResponse(
            RECORDS.values().stream()
                .map(record -> record.threadGroupDescriptor)
                .collect(Collectors.toList())));
  }

  /**
   * Interrupts (forcibly stops) a thread group by name.
   *
   * <p>The thread group is not fully guaranteed to get stopped, but it will not be returned from
   * the API in subsequent calls. TODO block until the thread is not found any more? Or force via OS
   */
  private static Http.Response<String> deleteThreadGroup(HttpExchange exchange) {
    final ThreadGroupRecord record = RECORDS.get(Rest.pathParam(exchange, "name"));

    if (record == null) {
      return Http.response(StatusCode.NOT_FOUND);
    } else {
      record.threadGroup.interrupt();
      RECORDS.remove(Rest.pathParam(exchange, "name"));
      return Http.response(StatusCode.NO_CONTENT);
    }
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
  private static Http.Response<GetThreadGroupHeartbeatResponse> getThreadGroupHeartbeat(
      HttpExchange exchange) {
    final ThreadGroupRecord record = RECORDS.get(Rest.pathParam(exchange, "name"));

    if (record == null) {
      return Http.response(
          StatusCode.NOT_FOUND, new GetThreadGroupHeartbeatResponse(List.of(), null));
    } else {
      return Http.response(StatusCode.OK, GetThreadGroupHeartbeatResponse.create(record.umbilical));
    }
  }
}
