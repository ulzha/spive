package io.ulzha.spive.scaler.app.lib;

import com.google.common.collect.ImmutableList;
import io.ulzha.spive.app.events.CreateInstance;
import io.ulzha.spive.basicrunner.api.Umbilical;
import io.ulzha.spive.lib.Event;
import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventIterator;
import io.ulzha.spive.lib.EventLog;
import io.ulzha.spive.lib.HandledException;
import io.ulzha.spive.lib.InternalException;
import io.ulzha.spive.lib.LockableEventLog;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import io.ulzha.spive.scaler.app.SpiveScaler;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface SpiveScalerInstance {
  void accept(final CreateInstance event);

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

        final EventIterator eventIterator = new EventIterator(inputEventLog.iterator());

        final UmbilicalWriter umbilicus = umbilical.new Umbilicus(() -> eventIterator.lastTimeRead);

        final SpiveScalerOutputGateway output =
            new SpiveScalerOutputGateway(umbilicus, eventIterator, wallClockTime, outputEventLog);

        final SpiveScaler app = new SpiveScaler(output);

        List<Runnable> workloads = new ArrayList<>();
        workloads.add(new EventLoop(umbilical, eventIterator, app, inputEventLog));
        workloads.addAll(selectWorkloads(app));

        umbilical.addHeartbeat(null); // marks start of all the workloads
        runWorkloads(workloads);
        LOG.info("Instance exiting nominally");
      } catch (ExecutionException e) {
        LOG.info("Workload failed:", e.getCause());
        umbilical.addError(null, e.getCause());
        throw new HandledException(e.getCause());
      } catch (InterruptedException e) {
        LOG.info("Workload interrupted:", e);
        umbilical.addError(null, e);
        Thread.currentThread().interrupt();
        throw new HandledException(e);
      } catch (Throwable t) {
        LOG.info("Workload failed:", t);
        umbilical.addError(null, t);
        throw t;
      } finally {
        LOG.info(
            "Instance exiting with K failed partitions, L stalling partitions, and M general workload failures");
      }
    }

    private static List<Runnable> selectWorkloads(SpiveScaler app) {
      return ImmutableList.of(app.new Watchdog());
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
      // (we might benefit from knowing about thread fanout, also CPU or IO boundness... TODO)
      final AtomicInteger threadCounter = new AtomicInteger();
      final ExecutorService executorService =
          Executors.newCachedThreadPool(
              (runnable) ->
                  new Thread(
                      runnable,
                      Thread.currentThread().getName()
                          + "-workload-"
                          + threadCounter.getAndIncrement()));
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
      private final EventIterator eventIterator;
      private final SpiveScaler app;
      private final LockableEventLog inputEventLog;

      private EventLoop(
          Umbilical umbilical,
          EventIterator eventIterator,
          SpiveScaler app,
          LockableEventLog inputEventLog) {
        this.umbilical = umbilical;
        this.eventIterator = eventIterator;
        this.app = app;
        this.inputEventLog = inputEventLog;
      }

      @Override
      public void run() {
        LOG.info("EventLoop over {} running", inputEventLog);
        while (eventIterator.hasNext()) {
          final EventEnvelope envelope = eventIterator.next();
          final Event event = envelope.unwrap();
          // TODO assert that the event belongs to the intended subset of partitions
          // TODO maintain a rolling hash and panic if inconsistency observed?
          umbilical.addHeartbeat(event.time);
          try {
            // The synchronization here ensures that emitIf runs serially with event handlers, and
            // that it never runs between an input event and its consequential event when emitted to
            // the same log.
            // TODO optimize to forego synchronization when no other workloads are running
            inputEventLog.lock();
            app.getClass().getMethod("accept", event.payload.getClass()).invoke(app, event.payload);
            inputEventLog.unlock();
            // There is intentionally no `finally` here. If we ensure unlock after an exception then
            // that may give workloads a short window before instance death where they can corrupt
            // the event sequence ahead of an intended consequential event. Leaving them deadlocked
            // when a handler throws is consistent.
          } catch (InvocationTargetException ite) {
            // cause seems to be in user code... But could be also from a Gateway call
            // TODO sanity check if gateway exceptions are swallowed by user code
            umbilical.addError(eventIterator.lastTimeRead, ite.getCause());
            throw new HandledException(ite.getCause());
          } catch (NoSuchMethodException | IllegalAccessException e) {
            umbilical.addError(eventIterator.lastTimeRead, e);
            throw new InternalException(
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
        LOG.info("EventLoop over {} completed", inputEventLog);
        // end of event log, so just exit normally
      }
    }
  }
}
