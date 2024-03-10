package io.ulzha.spive.lib;

import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import java.lang.reflect.InvocationTargetException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstanceMain {
  public static final Logger LOG = LoggerFactory.getLogger(InstanceMain.class);

  /**
   * Waits for the first workload to complete. The remaining workloads are then interrupted with a
   * not very long grace period and subsequently ignored. Depending on the return statuses, at that
   * point the Instance is considered completed or crashed, and gets deleted from Spīve.
   *
   * <p>For example, reaching the end of a Stream always results in EventLoop workload exiting,
   * which corresponds to a completed Instance resp. Process. Whereas a HttpServer thread crashing
   * would correspond to a crashed Instance, which Spīve would then normally respawn with replay.
   */
  protected static void runWorkloads(UmbilicalWriter umbilical, List<Runnable> workloads)
      throws InterruptedException {
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
    // TODO test interrupted behavior - InterruptedException should show on umbilical
    final String workloadName = workloadsByFuture.get(firstExitedFuture).getClass().getSimpleName();

    try {
      // either completes normally or has the actual workload failure propagate
      firstExitedFuture.get();
      LOG.info("{} workload completed nominally", workloadName);
    } catch (ExecutionException e) {
      LOG.info("{} workload failed", workloadName, e.getCause());
      if (e.getCause() instanceof HandledException) {
        // came via EventLoop (or from gateway?) or some other Spive-provided workload?
        // keep propagating it unwrapped of InvocationTargetException and ExecutionException
        throw (HandledException) e.getCause();
      } else {
        umbilical.addError(e.getCause());
        throw new HandledException(e.getCause());
      }
    } finally {
      // TODO exit all the threads when instance is deleted (they are daemon already, right?) -
      // yet allow state and workloads to live through storage failures. Oftentimes it would be
      // useful to keep serving read requests even when the event loop has crashed due to
      // permanent failure in persisting new changes, for example. (On intermittent failures the
      // gateway should just retry indefinitely.)
      LOG.warn(
          "Shutting down workloads immediately without capturing any useful detail. TODO improve");
      // TODO perhaps the event loop must be last, to prevent premature lock release?
      executorService.shutdownNow();
    }
  }

  protected static class EventLoop<T> implements Runnable {
    private final UmbilicalWriter umbilical;
    private final EventIterator eventIterator;
    private final T app;
    private final EventLock eventLock;

    public EventLoop(
        UmbilicalWriter umbilical, EventIterator eventIterator, T app, EventLock eventLock) {
      this.umbilical = umbilical;
      this.eventIterator = eventIterator;
      this.app = app;
      this.eventLock = eventLock;
    }

    @Override
    public void run() {
      LOG.info("EventLoop over {} running", eventIterator);
      while (eventIterator.hasNext()) {
        final EventEnvelope envelope = eventIterator.next();
        System.out.println("We have an envelope: " + envelope);
        final Event event = envelope.unwrap();
        System.out.println("We have an event: " + event);
        // TODO assert that the event belongs to the intended subset of partitions
        // TODO maintain a rolling hash and panic if inconsistency observed?
        umbilical.addHeartbeat();
        try {
          // The synchronization here ensures that emitIf runs serially with event handlers, and
          // that it never runs between an input event and its consequential event when emitted to
          // the same log.
          // TODO optimize to forego synchronization when no other workloads are running
          eventLock.lock();
          app.getClass().getMethod("accept", event.payload.getClass()).invoke(app, event.payload);
          eventLock.unlock();
          // There is intentionally no `finally` here. If we ensure unlock after an exception then
          // that may give workloads a short window before instance death where they can corrupt
          // the event sequence ahead of an intended consequential event. Leaving them deadlocked
          // when a handler throws is consistent.
        } catch (InvocationTargetException ite) {
          // cause seems to be in user code... But could be also from a Gateway call
          // TODO sanity check if gateway exceptions are swallowed by user code
          umbilical.addError(ite.getCause());
          // TODO proceed gracefully actually? Other partitions can be processed so long as the
          // crashed one sees no subsequent events? If event log stalls, keep serving read requests
          // for a while? Vice versa as well? Write requests can even be served against the
          // unaffected partitions?
          throw new HandledException(ite.getCause());
        } catch (NoSuchMethodException | IllegalAccessException e) {
          umbilical.addError(e);
          throw new InternalException(
              "Error invoking "
                  + app.getClass().getCanonicalName()
                  + ".accept() on "
                  + event.getClass().getCanonicalName()
                  + " - should never happen if, upon creating a process, the artifact was reliably checked.",
              e);
        }
        umbilical.addSuccess();
      }
      LOG.info("EventLoop over {} completed", eventIterator);
      // end of event log, so just exit normally
    }
  }
}
