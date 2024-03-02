package io.ulzha.spive.example.clicctracc.app.lib;

import com.google.common.collect.ImmutableList;
import io.ulzha.spive.basicrunner.api.Umbilical;
import io.ulzha.spive.example.clicctracc.app.CliccTracc;
import io.ulzha.spive.example.clicctracc.app.CliccTracc.Metronome;
import io.ulzha.spive.example.clicctracc.app.events.Clicc;
import io.ulzha.spive.lib.EventIterator;
import io.ulzha.spive.lib.EventLog;
import io.ulzha.spive.lib.EventTime;
import io.ulzha.spive.lib.HandledException;
import io.ulzha.spive.lib.InstanceMain;
import io.ulzha.spive.lib.LockableEventLog;
import io.ulzha.spive.lib.umbilical.UmbilicalWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface CliccTraccInstance {
  default void accept(final Clicc event) {}

  // Any neater type signature possible? Help ensure that exactly one of the two is implemented?
  default void accept(final Clicc event, final EventTime eventTime) {
    accept(event);
  }
  // TODO default void accept(final Clicc event, final Instant eventInstant) {}

  /**
   * Implements application's interaction boilerplate with a concrete processing runtime and event
   * store. Intended as a layer for enabling powerful mix-and-match options.
   *
   * <p>Despite being generated and replaceable, this layer should be a readable and debuggable part
   * of the stack for application developers. Should strive to eliminate any obscureness of late
   * dependency injection etc.
   */
  class Main extends InstanceMain {
    public static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(final Umbilical umbilical, final String... args) throws Exception {
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

        final EventIterator eventIterator = new EventIterator(inputEventLog.iterator());

        final UmbilicalWriter umbilicus = umbilical.new Umbilicus(() -> eventIterator.lastTimeRead);

        final CliccTraccOutputGateway output =
            new CliccTraccOutputGateway(umbilicus, eventIterator, wallClockTime, outputEventLog);

        final CliccTracc app = new CliccTracc(output);

        List<Runnable> workloads = new ArrayList<>();
        workloads.add(new EventLoop<CliccTracc>(umbilicus, eventIterator, app, inputEventLog));
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

    private static List<Runnable> selectWorkloads(CliccTracc app) {
      return ImmutableList.of(app.new Metronome());
    }
  }
}
