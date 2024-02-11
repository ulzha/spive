package io.ulzha.spive.basicrunner.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.verify.VerificationTimes;

public class BasicRunnerGatewayTest {
  private static ClientAndServer mock = new ClientAndServer(1080);

  @BeforeEach
  public void resetServer() {
    mock.reset();
  }

  @Test
  void whenRunnerRespondsCreated_startInstance_shouldSucceedWithNoWarningsAndNoErrors() {
    mock.when(request().withPath("/thread_groups")).respond(response().withStatusCode(201));
    final FakeUmbilicus umbilicus = new FakeUmbilicus();
    final BasicRunnerGateway sut = new BasicRunnerGateway(umbilicus, List.of("some-zone"));

    final RunThreadGroupRequest request =
        new RunThreadGroupRequest(
            new ThreadGroupDescriptor(
                "some-instance", "http://localhost:1080/some.jar", "SomeClass", List.of()));
    sut.startInstance(request, "http://localhost:1080");

    mock.verify(request().withPath("/thread_groups").withMethod("POST"), VerificationTimes.once());
    assertThat(umbilicus.warnings, empty());
    assertThat(umbilicus.errors, empty());
  }

  @Test
  void whenConnectException_startInstance_shouldRetryForever() {
    final FakeUmbilicus umbilicus = new FakeUmbilicus();
    final BasicRunnerGateway sut = new BasicRunnerGateway(umbilicus, List.of("some-zone"));

    final RunThreadGroupRequest request =
        new RunThreadGroupRequest(
            new ThreadGroupDescriptor(
                "some-instance", "http://localhost:1080/some.jar", "SomeClass", List.of()));

    // TODO pluggable Clock and pluggable sleep - demystify "6"
    assertBlocksAtLeast(
        Duration.ofSeconds(6),
        () -> {
          assertThat(umbilicus.warnings, not(empty()));
          assertThat(umbilicus.warnings, everyItem(isA(ConnectException.class)));
          assertThat(umbilicus.errors, empty());
        },
        () -> sut.startInstance(request, "http://neverneverhost:1080"));

    assertThat(umbilicus.warnings, everyItem(isA(ConnectException.class)));
    assertThat(umbilicus.errors, not(empty()));
  }

  /** assertBlocksAtLeastThisLongAndIsGracefullyInterruptible */
  private void assertBlocksAtLeast(final Duration d, final Runnable assertion, final Runnable r) {
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      final CompletableFuture<Boolean> exitedGracefully = new CompletableFuture<>();
      final Future<?> future =
          executor.submit(
              () -> {
                try {
                  r.run();
                } finally {
                  exitedGracefully.complete(true);
                }
              });
      try {
        future.get(d.toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        // expected
        assertion.run();
        future.cancel(true);
        try {
          exitedGracefully.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e1) {
          fail("Blocked for " + d + " as expected but could not be gracefully interrupted", e1);
        } catch (ExecutionException e1) {
          fail(e1);
        } catch (InterruptedException e1) {
          Thread.currentThread().interrupt();
          fail(e1);
        }
        return;
      } catch (ExecutionException e) {
        fail(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        fail(e);
      }
    }
    fail("Expected to block at least " + d);
  }
}
