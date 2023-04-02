package io.ulzha.spive.example.clicctracc.app.lib;

import com.google.common.collect.ImmutableList;
import io.ulzha.spive.example.clicctracc.app.CliccTracc;
import io.ulzha.spive.example.clicctracc.app.events.Clicc;
import io.ulzha.spive.lib.Event;
import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventLog;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.InternalSpiveException;
import io.ulzha.spive.lib.LockableEventLog;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import io.ulzha.spive.threadrunner.api.Umbilical;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface CliccTraccInstance {
  default void accept(final Clicc event) {
  }

  default void accept(final Clicc event, final EventTime eventTime) {
    accept(event);
  }

  /**
   * Implements application's interaction boilerplate with a concrete processing runtime and event
   * store. Intended as a layer for enabling powerful mix-and-match options.
   *
   * <p>Despite being generated and replaceable, this layer should be a readable and debuggable part
   * of the stack for application developers. Should strive to eliminate any obscureness of late
   * dependency injection etc.
   */
  class Main {
    public static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(final Umbilical umbilical, final String... args) throws Exception {
      // null until the first event is read
      final AtomicReference<EventTime> currentEventTime = new AtomicReference<>();
      final Supplier<Instant> wallClockTime = Instant::now;

      try (EventLog naiveInputEventLog = EventLog.open(args[1], args[2]);
          EventLog naiveOutputEventLog = EventLog.open(args[3], args[4])) {
        // Locking is intended to have effect iff output is the same as input.
        // Ugly way to code it?
        final LockableEventLog inputEventLog = new LockableEventLog(naiveInputEventLog);
        final LockableEventLog outputEventLog =
            (naiveInputEventLog == naiveOutputEventLog
                ? inputEventLog
                : new LockableEventLog(naiveOutputEventLog));

        final UmbilicalWriter umbilicus = umbilical.new Umbilicus(currentEventTime);

        final CliccTraccOutputGateway output =
            new CliccTraccOutputGateway(umbilicus, currentEventTime, wallClockTime, outputEventLog);

        // offer executorService and synchronizedExecutorService?
        // executorServiceFactory? awaitQuiescence on all?
        final CliccTracc app = new CliccTracc(output);

        List<Runnable> workloads = new ArrayList<>();
        workloads.add(
            new EventLoop(
                umbilical,
                currentEventTime, // TODO refactor this as sort of a position, in inputEventLog?
                // Pass an iterator only?
                app,
                inputEventLog));
        workloads.addAll(selectWorkloads(app));

        umbilical.addHeartbeat(null); // marks start of all the workloads
        runWorkloads(workloads);
      } catch (Throwable t) {
        // TODO differentiate between errors that propagate from gateways (already appended to the
        // sample) and event handler exceptions, and workload exceptions...?
        LOG.info("Instance failure", t);
        umbilical.addError(currentEventTime.get(), t);
        throw t;
        // TODO proceed with other partitions actually
      } finally {
        LOG.info("Instance exiting");
      }
    }

    private static List<Runnable> selectWorkloads(CliccTracc app) {
      return ImmutableList.of(app.new Trigger());
    }

    /**
     * Waits for the first workload to complete. The remaining workloads are then interrupted with a
     * not very long grace period and subsequently ignored. Depending on the return statuses, at
     * that point the Instance is considered completed or crashed, and gets deleted from Spīve.
     *
     * <p>For example, reaching the end of a Stream always results in EventLoop workload exiting,
     * which corresponds to a completed Instance resp. Process. Whereas a HttpServer thread crashing
     * would correspond to a crashed Instance, which Spīve would then normally respawn with replay.
     */
    private static void runWorkloads(List<Runnable> workloads)
        throws InterruptedException, ExecutionException {
      // yolo, not sure which ExecutorService is best API-wise or if we should or should not pass
      // them into workloads
      // (we might benefit from knowing about thread fanout, and from having a consistent
      // UncaughtExceptionHandler FIXME)
      // Have as few threadpools as possible, and preferably only use ForkJoinPool.commonPool?
      final ExecutorService executorService = Executors.newCachedThreadPool();
      final CompletionService<Runnable> lifetimeService =
          new ExecutorCompletionService<>(executorService);
      final Map<Future<Runnable>, Runnable> workloadsByFuture = new HashMap<>();

      // TODO only start them when caught up... not sure by which definition...
      // ...and pause if fallen behind again?
      for (Runnable workload : workloads) {
        final Future<Runnable> submittedFuture = lifetimeService.submit(workload, workload);
        workloadsByFuture.put(submittedFuture, workload);
      }
      final Future<Runnable> firstExitedFuture = lifetimeService.take();
      // TODO test interrupted behavior

      LOG.info("Workload exited: {}", workloadsByFuture.get(firstExitedFuture));
      // TODO exit all the threads when instance is deleted (they are daemon already, right?) -
      // yet allow state and workloads to live through storage failures. Oftentimes it would be
      // useful to keep serving read requests even when the event loop has crashed due to
      // permanent failure in persisting new changes, for example. (On intermittent failures the
      // gateway should just retry indefinitely.)
      LOG.warn(
          "Shutting down workloads immediately without capturing any useful detail. TODO improve");
      executorService.shutdownNow();

      // either completes normally or has the actual workload failure propagate
      firstExitedFuture.get();
    }

    private static class EventLoop implements Runnable {
      private final Umbilical umbilical;
      private final AtomicReference<EventTime> currentEventTime;
      private final CliccTracc app;
      private final LockableEventLog inputEventLog;

      private EventLoop(
          Umbilical umbilical,
          AtomicReference<EventTime> currentEventTime,
          CliccTracc app,
          LockableEventLog inputEventLog) {
        this.umbilical = umbilical;
        this.currentEventTime = currentEventTime;
        this.app = app;
        this.inputEventLog = inputEventLog;
      }

      @Override
      public void run() {
        for (EventEnvelope envelope : inputEventLog) {
          final Event event = envelope.unwrap();
          // TODO bail if the time is in distant future
          // TODO assert that the event belongs to the intended subset of partitions
          // TODO maintain a rolling hash and panic if inconsistency observed?
          umbilical.addHeartbeat(event.time);
          try {
            // The synchronization here ensures that emitIf runs serially with event handlers, and
            // that it never runs between an input event and its consequential event when emitted to
            // the same log.
            // For other purposes, workloads could be allowed to consume the reference without
            // synchronization... API TODO
            inputEventLog.lock();
            currentEventTime.set(event.time);
            app.getClass().getMethod("accept", event.payload.getClass()).invoke(app, event.payload);
            inputEventLog.unlock();
            // There is intentionally no `finally` here. If we ensure unlock after an exception then
            // that may give workloads a short window before instance death where they can corrupt
            // the event sequence ahead of an intended consequential event. Leaving them deadlocked
            // when a handler throws is consistent.
          } catch (InvocationTargetException ite) {
            // cause seems to be in user code... But could be also from a Gateway call
            // TODO retry with backoff if it is a retryable handler (note that we specify some
            // retrying responsibility on Gateway, so the need for retryable handlers is uncertain)
            // TODO sanity check if gateway exceptions are swallowed by user code
            // umbilical.addFailure(currentEventTime.get(), ite); the throw ought to reach
            // runWorkloads. TODO test
            throw new RuntimeException("Application failure", ite);
          } catch (NoSuchMethodException | IllegalAccessException e) {
            // umbilical.addFailure(currentEventTime.get(), e); the throw ought to reach
            // runWorkloads. TODO test
            throw new InternalSpiveException(
                "Error invoking "
                    + app.getClass().getCanonicalName()
                    + ".accept() on "
                    + event.getClass().getCanonicalName()
                    + " - should never happen if, upon creating a process, the artifact was reliably checked.",
                e);
          }
          umbilical.addSuccess(event.time);
          // TODO check umbilical for errors from gateway that may have been swallowed in accept()
        }
        // end of event log, so just exit normally
      }
    }
  }
}
