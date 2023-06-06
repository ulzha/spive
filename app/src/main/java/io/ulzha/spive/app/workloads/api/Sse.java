package io.ulzha.spive.app.workloads.api;

import com.linecorp.armeria.common.sse.ServerSentEvent;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.streaming.ServerSentEvents;
import io.ulzha.spive.app.model.Platform;
import java.time.Duration;
import reactor.core.publisher.Flux;

public class Sse {
  public static HttpService service(final Platform platform) {
    final Flux<ServerSentEvent> processesUpdated =
        Flux.<String>push(
                emitter -> {
                  final Thread waiter =
                      new Thread(
                          () -> {
                            try {
                              while (true) {
                                synchronized (platform) {
                                  platform.wait();
                                }
                                emitter.next(platform.processesEtag);
                              }
                            } catch (InterruptedException e) {
                              emitter.error(e);
                              Thread.currentThread().interrupt();
                              throw new RuntimeException(e);
                            }
                          },
                          "spive-api-sse-platform-waiter");
                  emitter.onDispose(waiter::interrupt);
                  waiter.start();
                })
            .distinctUntilChanged()
            // .map(etag -> ServerSentEvent.ofId(etag)) doesn't work without data
            .map(etag -> ServerSentEvent.builder().id(etag).data(etag).build())
            .cache(1);

    return (ctx, req) -> {
      ctx.setRequestTimeout(Duration.ofMinutes(5));
      return ServerSentEvents.fromPublisher(processesUpdated);
    };
  }
}
