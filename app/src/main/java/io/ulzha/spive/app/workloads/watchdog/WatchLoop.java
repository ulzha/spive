package io.ulzha.spive.app.workloads.watchdog;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.ulzha.spive.app.lib.SpiveOutputGateway;
import io.ulzha.spive.app.model.Platform;
import io.ulzha.spive.app.model.Process;
import io.ulzha.spive.util.InterruptableSchedulable;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class WatchLoop {
  private final Platform platform;
  private final SpiveOutputGateway output;

  private final ScheduledExecutorService pollExecutor = Executors.newScheduledThreadPool(2);
  private final HttpClient httpClient = HttpClient.newHttpClient();
  // Could store loop reference in Instance directly as a member... Why not? It slightly pollutes
  // state for other workloads? Superfluous in a sense that it is unnecessary until caught up?
  // Unnecessary to snapshot also?
  private final Map<Process.Instance, ScheduledFuture<?>> loops = new HashMap<>();

  public WatchLoop(final Platform platform, final SpiveOutputGateway output) {
    this.platform = platform;
    this.output = output;
  }

  public void watchOnce() {
    // init polling loops for instances that lack them
    for (Process.Instance instance : platform.instancesById.values()) {
      loops.computeIfAbsent(
          instance,
          it -> {
            final PollLoop pollLoop =
                new PollLoop(it, new Placenta(httpClient, it), Instant::now, output);
            return pollExecutor.scheduleAtFixedRate(
                new InterruptableSchedulable(pollLoop::pollOnce), 0, 10, SECONDS);
          });
    }

    // stop polling of deleted instances
    for (var entry : loops.entrySet()) {
      if (entry.getKey().process == null) {
        // entry.getValue().isDone() may still be false, poll loop does not give up on its own
        entry.getValue().cancel(true);
        loops.remove(entry.getKey());
      }
    }
  }
}
