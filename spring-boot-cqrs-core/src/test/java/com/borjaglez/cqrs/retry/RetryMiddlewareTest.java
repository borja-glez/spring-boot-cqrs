package com.borjaglez.cqrs.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestEvent;
import com.borjaglez.cqrs.fixtures.TestQuery;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.middleware.MiddlewareChain;
import com.borjaglez.cqrs.tracing.TracingMiddleware;

class RetryMiddlewareTest {

  private final List<Duration> sleeps = new ArrayList<>();

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void succeedsAfterTransientFailures() throws Exception {
    RetryMiddleware middleware = middleware(RetryPolicy.defaults());
    FailingChain chain = new FailingChain(2, () -> new IllegalStateException("conflict"));

    Object result = middleware.process(new TestCommand("data"), chain);

    assertThat(result).isEqualTo("ok");
    assertThat(chain.calls).isEqualTo(3);
    assertThat(sleeps).hasSize(2);
  }

  @Test
  void queryResultIsReturnedWhenLastAttemptSucceeds() throws Exception {
    RetryMiddleware middleware = middleware(RetryPolicy.defaults());
    FailingChain chain = new FailingChain(2, () -> new IllegalStateException("timeout"));

    assertThat(middleware.process(new TestQuery("q"), chain)).isEqualTo("ok");
    assertThat(chain.calls).isEqualTo(3);
  }

  @Test
  void exhaustedAttemptsRethrowOriginalException() {
    RetryMiddleware middleware = middleware(RetryPolicy.defaults());
    List<RuntimeException> thrown = new ArrayList<>();
    FailingChain chain =
        new FailingChain(
            Integer.MAX_VALUE,
            () -> {
              RuntimeException e = new IllegalStateException("conflict " + thrown.size());
              thrown.add(e);
              return e;
            });

    assertThatThrownBy(() -> middleware.process(new TestCommand("data"), chain))
        .isSameAs(thrown.get(2));
    assertThat(chain.calls).isEqualTo(3);
    assertThat(sleeps).hasSize(2);
  }

  @Test
  void nonRetriableExceptionFailsImmediately() {
    RetryMiddleware middleware = middleware(RetryPolicy.defaults());
    IllegalArgumentException failure = new IllegalArgumentException("bad");
    FailingChain chain = new FailingChain(Integer.MAX_VALUE, () -> failure);

    assertThatThrownBy(() -> middleware.process(new TestCommand("data"), chain)).isSameAs(failure);
    assertThat(chain.calls).isEqualTo(1);
    assertThat(sleeps).isEmpty();
  }

  @Test
  void nonRetriableCauseFailsImmediately() {
    RetryMiddleware middleware = middleware(RetryPolicy.defaults());
    RuntimeException failure = new IllegalStateException(new IllegalArgumentException("bad"));
    FailingChain chain = new FailingChain(Integer.MAX_VALUE, () -> failure);

    assertThatThrownBy(() -> middleware.process(new TestCommand("data"), chain)).isSameAs(failure);
    assertThat(chain.calls).isEqualTo(1);
  }

  @Test
  void checkedExceptionIsClassifiedLikeAnyOther() throws Exception {
    RetryMiddleware notRetried = middleware(RetryPolicy.defaults());
    FailingChain first = new FailingChain(1, () -> new IOException("io"));
    assertThatThrownBy(() -> notRetried.process(new TestCommand("data"), first))
        .isInstanceOf(IOException.class);
    assertThat(first.calls).isEqualTo(1);

    RetryMiddleware retried = middleware(RetryPolicy.builder().retryOn(IOException.class).build());
    FailingChain second = new FailingChain(1, () -> new IOException("io"));
    assertThat(retried.process(new TestCommand("data"), second)).isEqualTo("ok");
    assertThat(second.calls).isEqualTo(2);
  }

  @Test
  void eventsPassThroughUntouched() {
    RetryMiddleware middleware = middleware(RetryPolicy.defaults());
    FailingChain chain = new FailingChain(1, () -> new IllegalStateException("conflict"));

    assertThatThrownBy(() -> middleware.process(new TestEvent("e"), chain))
        .isInstanceOf(IllegalStateException.class);
    assertThat(chain.calls).isEqualTo(1);
    assertThat(sleeps).isEmpty();
  }

  @Test
  void otherMessagesPassThroughUntouched() throws Exception {
    RetryMiddleware middleware = middleware(RetryPolicy.defaults());
    FailingChain chain = new FailingChain(0, IllegalStateException::new);

    assertThat(middleware.process("plain", chain)).isEqualTo("ok");
    assertThat(chain.calls).isEqualTo(1);
  }

  @Test
  void maxAttemptsOneNeverSleeps() {
    RetryMiddleware middleware = middleware(RetryPolicy.builder().maxAttempts(1).build());
    FailingChain chain = new FailingChain(1, () -> new IllegalStateException("conflict"));

    assertThatThrownBy(() -> middleware.process(new TestCommand("data"), chain))
        .isInstanceOf(IllegalStateException.class);
    assertThat(chain.calls).isEqualTo(1);
    assertThat(sleeps).isEmpty();
  }

  @Test
  void perTypeOverrideAndNoRetry() throws Exception {
    RetryMiddleware middleware =
        RetryMiddleware.builder()
            .defaultPolicy(RetryPolicy.noRetry())
            .override(TestCommand.class, RetryPolicy.builder().maxAttempts(4).build())
            .override(TestQuery.class, RetryPolicy.noRetry())
            .sleeper(sleeps::add)
            .build();

    FailingChain commandChain = new FailingChain(3, () -> new IllegalStateException("x"));
    assertThat(middleware.process(new TestCommand("data"), commandChain)).isEqualTo("ok");
    assertThat(commandChain.calls).isEqualTo(4);

    FailingChain queryChain = new FailingChain(1, () -> new IllegalStateException("x"));
    assertThatThrownBy(() -> middleware.process(new TestQuery("q"), queryChain))
        .isInstanceOf(IllegalStateException.class);
    assertThat(queryChain.calls).isEqualTo(1);

    FailingChain otherChain = new FailingChain(1, () -> new IllegalStateException("x"));
    assertThatThrownBy(() -> middleware.process(new OtherCommand(), otherChain))
        .isInstanceOf(IllegalStateException.class);
    assertThat(otherChain.calls).isEqualTo(1);

    assertThat(middleware.policyFor(TestCommand.class).maxAttempts()).isEqualTo(4);
    assertThat(middleware.policyFor(OtherCommand.class)).isEqualTo(RetryPolicy.noRetry());
    assertThat(middleware.defaultPolicy()).isEqualTo(RetryPolicy.noRetry());
  }

  @Test
  void overrideLooksUpExactClassOnly() {
    RetryMiddleware middleware =
        RetryMiddleware.builder()
            .override(TestCommand.class, RetryPolicy.noRetry())
            .sleeper(sleeps::add)
            .build();

    assertThat(middleware.policyFor(SubCommand.class)).isEqualTo(RetryPolicy.defaults());
  }

  @Test
  void delaysFollowTheBackoffStrategy() throws Exception {
    RetryMiddleware middleware =
        middleware(
            RetryPolicy.builder()
                .maxAttempts(4)
                .backoff(
                    BackoffStrategy.exponential(Duration.ofMillis(100), 2.0, Duration.ofSeconds(1)))
                .build());
    FailingChain chain = new FailingChain(3, () -> new IllegalStateException("conflict"));

    middleware.process(new TestCommand("data"), chain);

    assertThat(sleeps)
        .containsExactly(Duration.ofMillis(100), Duration.ofMillis(200), Duration.ofMillis(400));
  }

  @Test
  void jitterUsesInjectedRandomSource() throws Exception {
    RetryMiddleware middleware =
        RetryMiddleware.builder()
            .defaultPolicy(
                RetryPolicy.builder()
                    .backoff(
                        BackoffStrategy.exponentialWithJitter(
                            Duration.ofMillis(100), 2.0, Duration.ofSeconds(1), 0.1))
                    .build())
            .sleeper(sleeps::add)
            .random(() -> BackoffStrategyTest.fixedRandom(0.0))
            .build();
    FailingChain chain = new FailingChain(2, () -> new IllegalStateException("conflict"));

    middleware.process(new TestCommand("data"), chain);

    assertThat(sleeps).containsExactly(Duration.ofMillis(90), Duration.ofMillis(180));
  }

  @Test
  void interruptWhileWaitingRestoresFlagAndRethrowsLastFailure() {
    RetryMiddleware middleware =
        RetryMiddleware.builder()
            .sleeper(
                delay -> {
                  throw new InterruptedException("stop");
                })
            .build();
    IllegalStateException failure = new IllegalStateException("conflict");
    FailingChain chain = new FailingChain(Integer.MAX_VALUE, () -> failure);

    assertThatThrownBy(() -> middleware.process(new TestCommand("data"), chain))
        .isSameAs(failure)
        .satisfies(
            e ->
                assertThat(e.getSuppressed())
                    .singleElement()
                    .isInstanceOf(InterruptedException.class));
    assertThat(chain.calls).isEqualTo(1);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  void defaultSleeperAndRandomAreUsedWhenNotInjected() throws Exception {
    RetryMiddleware middleware =
        RetryMiddleware.builder()
            .defaultPolicy(
                RetryPolicy.builder()
                    .backoff(
                        BackoffStrategy.exponentialWithJitter(
                            Duration.ofMillis(1), 1.0, Duration.ofMillis(1), 0.5))
                    .build())
            .build();
    FailingChain chain = new FailingChain(1, () -> new IllegalStateException("conflict"));

    assertThat(middleware.process(new TestCommand("data"), chain)).isEqualTo("ok");
    assertThat(chain.calls).isEqualTo(2);
  }

  @Test
  void everyDownstreamMiddlewareRunsOnEachAttempt() throws Exception {
    AtomicInteger downstreamCalls = new AtomicInteger();
    AtomicInteger terminalCalls = new AtomicInteger();
    BusMiddleware downstream =
        (message, chain) -> {
          downstreamCalls.incrementAndGet();
          return chain.proceed(message);
        };
    MiddlewareChain terminal =
        message -> {
          if (terminalCalls.incrementAndGet() < 3) {
            throw new IllegalStateException("conflict");
          }
          return "done";
        };

    Object result =
        new DefaultMiddlewareChain(
                List.of(middleware(RetryPolicy.defaults()), downstream), terminal)
            .proceed(new TestCommand("data"));

    assertThat(result).isEqualTo("done");
    assertThat(downstreamCalls).hasValue(3);
    assertThat(terminalCalls).hasValue(3);
  }

  @Test
  void orderedAfterContextAndTracingAndBeforeUnorderedMiddleware() {
    assertThat(RetryMiddleware.ORDER).isEqualTo(Ordered.LOWEST_PRECEDENCE - 100);
    assertThat(RetryMiddleware.class.getAnnotation(Order.class).value())
        .isEqualTo(RetryMiddleware.ORDER);

    RetryMiddleware retry = middleware(RetryPolicy.defaults());
    BusMiddleware unordered = (message, chain) -> chain.proceed(message);
    BusMiddleware tracing = new TracingMiddleware(null, null);
    BusMiddleware context = new ContextPropagationMiddleware(true, List.of());
    List<BusMiddleware> middlewares = new ArrayList<>(List.of(unordered, retry, tracing, context));

    AnnotationAwareOrderComparator.sort(middlewares);

    assertThat(middlewares).containsExactly(context, tracing, retry, unordered);
  }

  private RetryMiddleware middleware(RetryPolicy policy) {
    return RetryMiddleware.builder().defaultPolicy(policy).sleeper(sleeps::add).build();
  }

  static class OtherCommand extends Command {}

  static class SubCommand extends TestCommand {}

  private static final class FailingChain implements MiddlewareChain {
    private final int failures;
    private final ExceptionSupplier failure;
    private int calls;

    FailingChain(int failures, ExceptionSupplier failure) {
      this.failures = failures;
      this.failure = failure;
    }

    @Override
    public Object proceed(Object message) throws Exception {
      calls++;
      if (calls <= failures) {
        throw failure.get();
      }
      return "ok";
    }
  }

  @FunctionalInterface
  private interface ExceptionSupplier {
    Exception get();
  }

  @Test
  void runsLocallyAndOnTheReceiverButNotOnTheSender() {
    assertThat(RetryMiddleware.builder().build().phases())
        .containsExactlyInAnyOrder(DispatchPhase.LOCAL, DispatchPhase.INBOUND);
  }
}
