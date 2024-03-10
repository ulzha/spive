// Generated by io.ulzha.spive.codegen.GenerateIocCode - do not edit! Put application logic in Copy
// class that implements CopyInstance interface.
package io.ulzha.spive.example.copy.app.spive.gen;

import com.google.common.collect.ImmutableList;
import io.ulzha.spive.basicrunner.api.Umbilical;
import io.ulzha.spive.example.copy.app.Copy;
import io.ulzha.spive.example.copy.app.events.CreateFoo;
import io.ulzha.spive.lib.EventIterator;
import io.ulzha.spive.lib.EventLock;
import io.ulzha.spive.lib.EventLog;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.HandledException;
import io.ulzha.spive.lib.InstanceMain;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// basicrunner.*Umbilical seems like an ugly dependency. gateways.BasicRunnerUmbilical? core.util?

/**
 * Glue code generated by Spīve, which facilitates strongly typed input and output.
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
 * this state, nor create side effects through gateways, except for emitting events through {@code
 * output} gateway.
 */
public interface CopyInstance {

  default void accept(final CreateFoo event) {}

  default void accept(final CreateFoo event, final EventTime eventTime) {
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
  // just instance utils? No need to extend?
  class Main extends InstanceMain {
    public static final Logger LOG = LoggerFactory.getLogger(Main.class);

    // useful for running outside framework? Spawn some sort of CLI umbilical?
    //    public static void main(final String... args) {
    //    }

    public static void main(final Umbilical umbilical, final String... args) throws Exception {
      final Supplier<Instant> wallClockTime = Instant::now;
      String logExitStatus = "nominally";

      try (EventLog inputEventLog = EventLog.open(args[0], args[1]);
          EventLog outputEventLog = EventLog.open(args[2], args[3])) {
        final EventIterator eventIterator = new EventIterator(inputEventLog.iterator());

        // FIXME thread-safe supplier
        // FIXME beat outside an event when the write is actually from a concurrent workload
        final UmbilicalWriter umbilicus = umbilical.new Umbilicus(() -> eventIterator.lastTimeRead);

        final EventLock eventLock = new EventLock();
        final CopyOutputGateway output =
            new CopyOutputGateway(umbilicus, eventIterator, wallClockTime, eventLock);

        final Copy app = new Copy(output);

        List<Runnable> workloads = new ArrayList<>();
        workloads.add(new EventLoop<Copy>(umbilicus, eventIterator, app, eventLock));
        workloads.addAll(selectWorkloads(app, args[5]));

        umbilical.addHeartbeat(null); // marks start of all the workloads
        runWorkloads(umbilical.new Umbilicus(() -> null), workloads);
      } catch (InterruptedException e) {
        logExitStatus = "after being interrupted";
        // TODO was duplicated on the event, if any. That's intended: interruption cross-cuts
        umbilical.addError(null, e);
        Thread.currentThread().interrupt();
      } catch (HandledException e) {
        logExitStatus = "after handling an error";
      } catch (Throwable t) {
        // TODO differentiate between errors that propagate from gateways (already appended to the
        // sample) and event handler exceptions, and workload exceptions...?
        logExitStatus = "abnormally";
        // Unlike inside event loop, here our best option is to add error at instance level, and not
        // associate it with currentEventTime, because that event may have already been successfully
        // handled, and a success followed by an error would make a confusing sequence of updates.
        // Runner takes care of adding and logging with stacktrace.
        throw t;
      } finally {
        LOG.info(
            "Copy instance exiting {}, with {}",
            logExitStatus,
            "K failed partitions, L stalling partitions, and M general workload failures");
      }
    }

    /* make overridable? Or instead, move out of generated spive.gen and subject to optimizer rules */
    private static List<Runnable> selectWorkloads(Copy app, final String workloads) {
      // TODO the rules for workload selection, unsure about them - when to keep a single replica,
      // when to keep redundant replicas? Stuckness is not an easy signal to obtain. Will always
      // task users with providing a health check? Exercise: shard Watchdog but not Frontend?
      // for now just interpret all the inner Runnable classes as workloads
      // TODO with generation this should be done without reflection perhaps?
      //      for (Class<?> cls : app.getClass().getDeclaredClasses()) {
      //        if (!Modifier.isStatic(cls.getModifiers())) {
      //          final Constructor<?> ctor = cls.getDeclaredConstructor(app.getClass());
      //          final Runnable workload = ctor.newInstance(app);
      //        }
      //      }
      if (workloads.equals("event loop only")) {
        return ImmutableList.of();
      } else {
        return ImmutableList.of();
      }
    }
  }
}
