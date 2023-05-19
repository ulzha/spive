package io.ulzha.spive.app.lib;

import com.google.common.collect.ImmutableList;
import io.ulzha.spive.app.Spive;
import io.ulzha.spive.app.events.CreateEventLog;
import io.ulzha.spive.app.events.CreateInstance;
import io.ulzha.spive.app.events.CreateProcess;
import io.ulzha.spive.app.events.CreateStream;
import io.ulzha.spive.app.events.CreateType;
import io.ulzha.spive.app.events.DeleteInstance;
import io.ulzha.spive.app.events.DeleteProcess;
import io.ulzha.spive.app.events.InstanceProgress;
import io.ulzha.spive.app.events.InstanceStatusChange;
import io.ulzha.spive.lib.Event;
import io.ulzha.spive.lib.EventEnvelope;
import io.ulzha.spive.lib.EventLog;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.InternalSpiveException;
import io.ulzha.spive.lib.LockableEventLog;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import io.ulzha.spive.threadrunner.api.ThreadRunnerGateway;
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

// threadrunner.*Umbilical seems like an ugly dependency. gateways.ThreadRunnerUmbilical? core.util?

/**
 * Glue code generated by Spīve, which facilitates strongly typed input and output.
 *
 * <p>TODO generate, also the accompanying doc below
 *
 * <p>Besides event handling, your Foo application may also comprise lightweight background
 * workloads, unpredictable query serving workloads, long running heavy computation workloads, etc.
 * Implement them as inner classes which implement Runnable, and Spīve platform will pick them up;
 * see Workloads doc. (TODO without reflection? more like an explicit registerWorkloads()?)
 *
 * <p>Note that as soon as one run() method exits, the instance is considered "done", the rest of
 * run() methods get interrupted as well, and the instance is stopped.
 *
 * <p>The workload Runnables may get autoscaled independently from each other. A given Foo instance
 * runs at most one instance of each workload. Apart from this guarantee, your code should not make
 * assumptions as to which (if any) workload Runnables coexist on a given instance of Foo.
 *
 * <p>The workload Runnables can freely rely on in-memory state of Foo, but they should never modify
 * it nor create side effects through gateways, except emitting events through EventStoreGateway.
 */
public interface SpiveInstance
/*BackgroundfulInstance, ServerfulInstance, Supplier<Runnable>*/ {

  void accept(final InstanceStatusChange event);

  void accept(final InstanceProgress event);

  void accept(final CreateEventLog event);

  void accept(final CreateInstance event);

  void accept(final CreateProcess event);

  void accept(final CreateStream event);

  void accept(final CreateType event);

  void accept(final DeleteInstance event);

  void accept(final DeleteProcess event);

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

    // useful for running outside framework? Spawn some sort of CLI umbilical?
    //    public static void main(final String... args) {
    //    }

    public static void main(final Umbilical umbilical, final String... args) throws Exception {
      // null until the first event is read
      final AtomicReference<EventTime> currentEventTime = new AtomicReference<>();
      final Supplier<Instant> wallClockTime = Instant::now;

      try (EventLog naiveInputEventLog = EventLog.open(args[0], args[1]);
          EventLog naiveOutputEventLog = EventLog.open(args[2], args[3])) {
        // Locking is intended to have effect iff output is the same as input.
        // Ugly way to code it?
        final LockableEventLog inputEventLog = new LockableEventLog(naiveInputEventLog);
        final LockableEventLog outputEventLog =
            (naiveInputEventLog == naiveOutputEventLog
                ? inputEventLog
                : new LockableEventLog(naiveOutputEventLog));

        final UmbilicalWriter umbilicus = umbilical.new Umbilicus(currentEventTime);

        final SpiveOutputGateway output =
            new SpiveOutputGateway(umbilicus, currentEventTime, wallClockTime, outputEventLog);

        // FIXME zones that this instance manages should come from its state, not from args
        final ThreadRunnerGateway runner =
            new ThreadRunnerGateway(umbilicus, List.of(args[4].split(",")));
        // TODO parse arguments in a more structured way

        // offer executorService and synchronizedExecutorService?
        // executorServiceFactory? awaitQuiescence on all?
        final Spive app = new Spive(output, runner);

        List<Runnable> workloads = new ArrayList<>();
        workloads.add(
            new EventLoop(
                umbilical,
                currentEventTime, // TODO refactor this as sort of a position, in inputEventLog?
                // Pass an iterator only?
                app,
                inputEventLog));
        workloads.addAll(selectWorkloads(app, args[5]));

        umbilical.addHeartbeat(null); // marks start of all the workloads
        runWorkloads(workloads);
      } catch (Throwable t) {
        // TODO differentiate between errors that propagate from gateways (already appended to the
        // sample) and event handler exceptions, and workload exceptions...?
        LOG.info("Instance failure", t);
        // Unlike inside event loop, here our best option is to add error at instance level, and not
        // associate it with currentEventTime, because that event may have already been successfully
        // handled, and a success followed by an error would make a confusing sequence of updates.
        umbilical.addError(null, t);
        throw t;
        // TODO proceed with other partitions actually
      } finally {
        LOG.info("Instance exiting");
      }
    }

    /* move to SpiveInstance and make overridable? Or instead, subject to optimizer rules */
    private static List<Runnable> selectWorkloads(Spive spive, final String workloads) {
      // TODO the rules for workload selection, unsure about them - when to keep a single replica,
      // when to keep redundant replicas? Stuckness is not an easy signal to obtain. Will always
      // task users with providing a health check? Exercise: shard Watchdog but not Frontend?
      // for now just interpret all the inner Runnable classes as workloads
      // TODO with generation this should be done without reflection perhaps?
      //      for (Class<?> cls : spive.getClass().getDeclaredClasses()) {
      //        if (!Modifier.isStatic(cls.getModifiers())) {
      //          final Constructor<?> ctor = cls.getDeclaredConstructor(spive.getClass());
      //          final Runnable workload = ctor.newInstance(spive);
      //        }
      //      }
      if (workloads.equals("event loop only")) {
        return ImmutableList.of();
      } else {
        return ImmutableList.of(spive.new Watchdog(), spive.new Frontend());
      }
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

    //    TODO abstract class with parent, like picocli does?

    //    public static Main getInstance() {
    //      if (singletonInstance == null) {
    //        singletonInstance = new Main();
    //      }
    //      return singletonInstance;
    //    }

    private static class EventLoop implements Runnable {
      private final Umbilical umbilical;
      private final AtomicReference<EventTime> currentEventTime;
      private final Spive spive;
      private final LockableEventLog inputEventLog;

      private EventLoop(
          Umbilical umbilical,
          AtomicReference<EventTime> currentEventTime,
          Spive spive,
          LockableEventLog inputEventLog) {
        this.umbilical = umbilical;
        this.currentEventTime = currentEventTime;
        this.spive = spive;
        this.inputEventLog = inputEventLog;
      }

      @Override
      public void run() {
        LOG.info("Starting event loop over " + inputEventLog);
        for (EventEnvelope envelope : inputEventLog) {
          System.out.println("We have an envelope: " + envelope);
          final Event event = envelope.unwrap();
          System.out.println("We have an event: " + event);
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
            spive
                .getClass()
                .getMethod("accept", event.payload.getClass())
                .invoke(spive, event.payload);
            inputEventLog.unlock();
            // There is intentionally no `finally` here. If we ensure unlock after an exception then
            // that may give workloads a short window before instance death where they can corrupt
            // the event sequence ahead of an intended consequential event. Leaving them deadlocked
            // when a handler throws is consistent.
          } catch (InvocationTargetException ite) {
            // cause seems to be in user code... But could be also from a Gateway call
            // TODO sanity check if gateway exceptions are swallowed by user code
            umbilical.addError(currentEventTime.get(), ite);
            throw new RuntimeException("Application failure", ite);
          } catch (NoSuchMethodException | IllegalAccessException e) {
            umbilical.addError(currentEventTime.get(), e);
            throw new InternalSpiveException(
                "Error invoking "
                    + spive.getClass().getCanonicalName()
                    + ".accept() on "
                    + event.getClass().getCanonicalName()
                    + " - should never happen if, upon creating a process, the artifact was reliably checked.",
                e);
          }
          umbilical.addSuccess(event.time);
          // TODO check umbilical for errors from gateway that may have been swallowed in accept()
        }
        LOG.info("Stopping the event loop");
        // end of event log, so just exit normally
      }
    }
  }
}
