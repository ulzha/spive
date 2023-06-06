package io.ulzha.spive.scaler.app;

import io.ulzha.spive.app.events.CreateInstance;
import io.ulzha.spive.app.model.Platform;
import io.ulzha.spive.app.model.Process;
import io.ulzha.spive.scaler.app.lib.SpiveScalerInstance;
import io.ulzha.spive.scaler.app.lib.SpiveScalerOutputGateway;

/**
 * Would be best to not poll... Make its input an example of an ephemeral stream that doesn't keep
 * the entire history? Or make it compacted by this application? By another application?
 */
public class SpiveScaler implements SpiveScalerInstance {

  // TODO showcase how to replicate Spive app model (partially?)
  private final Platform platform;
  private final SpiveScalerOutputGateway output;
  //  private final SpiveGateway spive;

  public SpiveScaler(final SpiveScalerOutputGateway output /*, SpiveGateway spive*/) {
    this.output = output;
    // this.spive = spive;
    platform = new Platform("com.company.dev"); // that's not showcasing replication, is it?
  }

  @Override
  public void accept(final CreateInstance event) {
    // TODO
    output.emitConsequential(/* ... */ );
  }

  public class Watchdog implements Runnable {
    @Override
    public void run() {
      while (true) {
        // synchronized(this.lastEventTime) {...}
        for (Process process : platform.processesById.values()) {
          for (Process.Instance instance : process.instances) {
            // TODO ensure that instances are running and healthy/making steady progress (some
            // statistics needed)
            // accumulate average and stddev for:
            //  * happy path processing time per event (with decay, or some coefficient related to
            // number of input partitions and volume of events). If we hit a brick wall trying to
            // maintain this then we know that a system downstream of gateway has become a
            // bottleneck?
            //  * same for replay mode - we want the catch-up to take limited time (configurable).
            // We are probably able to see here whether the throughput is bound by storage read or
            // event handling time
            //  * error (gateway retry a.k.a. warning) rates
            //    * plus things like MTTR? To detect when there's service degradation and perhaps to
            // account for services which might keep having intermittent failures or go into
            // maintenance often
            //    * aware of non-retryable errors?
            //    * aware of resource errors (for which backoff or scaling may help)
            //    * aware of almost errors (approaching OOM and such)
            //  * what when there's influx of events and we start lagging behind wall clock too
            // much? Not captured by the above
            //  * slowest key set, failing key set - not part of KTLO? Still have to collect it, or
            // how can Scaler request this otherwise? Since we sample anyway, we can proxy sampling
            // requests from Scaler?
            //  * request latency - a different story, not part of KTLO? I guess it wouldn't be easy
            // to track workload-specific resources

            // TODO deduplicate redundant side effects as a best effort...
            // May be useful to enforce a time handicap for redundant instances, so that in most
            // cases
            // only the leading one (per partition) performs side effects, and the trailing ones
            // hear
            // about it before issuing any concurrent calls that incur congestion. Or time handicap
            // for events not written by self...
            // TODO maintain at least one "pilot" instance with no workloads selected?
            // TODO maintain at least one "debug" instance with debug logs and periodic profiling
            // enabled? Spawn such instances specifically for the failing key set, too
            // TODO maintain at least one "endurance" instance, to surface bugs and edge cases that
            // rarely manifest?

            // TODO use failing key set to try to proceed with the rest of partitions

            // TODO alert user if it can't be helped
            System.out.println(instance.status);
          }
        }

        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
      }
    }
  }
}
