package com.borjaglez.cqrs.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.command.CommandNotRegisteredException;
import com.borjaglez.cqrs.fixtures.TestCommand;
import com.borjaglez.cqrs.fixtures.TestQuery;
import com.borjaglez.cqrs.idempotency.DuplicateMessageException;
import com.borjaglez.cqrs.query.QueryNotRegisteredException;

class RetryPolicyTest {

  @Test
  void defaultsRetryRuntimeExceptionsThreeTimesWithJitteredBackoff() {
    RetryPolicy policy = RetryPolicy.defaults();

    assertThat(policy.maxAttempts()).isEqualTo(3).isEqualTo(RetryPolicy.DEFAULT_MAX_ATTEMPTS);
    assertThat(policy.backoff()).isEqualTo(BackoffStrategy.defaults());
    assertThat(policy.retriableExceptions())
        .containsExactlyInAnyOrderElementsOf(RetryPolicy.DEFAULT_RETRIABLE_EXCEPTIONS)
        .containsExactly(RuntimeException.class);
    assertThat(policy.nonRetriableExceptions())
        .containsExactlyInAnyOrder(
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            CommandNotRegisteredException.class,
            QueryNotRegisteredException.class,
            DuplicateMessageException.class)
        .containsExactlyInAnyOrderElementsOf(RetryPolicy.DEFAULT_NON_RETRIABLE_EXCEPTIONS);
  }

  @Test
  void runtimeExceptionIsRetriableByDefault() {
    assertThat(RetryPolicy.defaults().isRetriable(new IllegalStateException("conflict"))).isTrue();
  }

  @Test
  void defaultNonRetriableExceptionsAreNotRetriable() {
    RetryPolicy policy = RetryPolicy.defaults();

    assertThat(policy.isRetriable(new IllegalArgumentException("bad"))).isFalse();
    assertThat(policy.isRetriable(new NumberFormatException("subtype of IAE"))).isFalse();
    assertThat(policy.isRetriable(new ConstraintViolationException(Set.of()))).isFalse();
    assertThat(policy.isRetriable(new CommandNotRegisteredException(TestCommand.class))).isFalse();
    assertThat(policy.isRetriable(new QueryNotRegisteredException(TestQuery.class))).isFalse();
    assertThat(policy.isRetriable(new DuplicateMessageException("orders#handle", "id"))).isFalse();
  }

  @Test
  void checkedExceptionIsNotRetriableByDefault() {
    assertThat(RetryPolicy.defaults().isRetriable(new IOException("io"))).isFalse();
  }

  @Test
  void checkedExceptionWrappedInRuntimeExceptionIsRetriable() {
    assertThat(
            RetryPolicy.defaults()
                .isRetriable(new CommandHandlerExecutionException(new IOException("io"))))
        .isTrue();
  }

  @Test
  void nonRetriableCauseWins() {
    RuntimeException wrapped = new IllegalStateException(new IllegalArgumentException("bad"));

    assertThat(RetryPolicy.defaults().isRetriable(wrapped)).isFalse();
  }

  @Test
  void retriableCauseMakesWrapperRetriable() {
    RetryPolicy policy = RetryPolicy.builder().retryOn(TimeoutException.class).build();

    assertThat(policy.isRetriable(new Exception(new TimeoutException("slow")))).isTrue();
    assertThat(policy.isRetriable(new IllegalStateException("not listed"))).isFalse();
  }

  @Test
  void nullIsNotRetriable() {
    assertThat(RetryPolicy.defaults().isRetriable(null)).isFalse();
  }

  @Test
  void causeCycleTerminates() {
    Exception first = new Exception("first");
    Exception second = new Exception("second", first);
    first.initCause(second);

    assertThat(RetryPolicy.defaults().isRetriable(first)).isFalse();
    assertThat(RetryPolicy.builder().retryOn(Exception.class).build().isRetriable(first)).isTrue();
  }

  @Test
  void shouldRetryHonoursMaxAttempts() {
    RetryPolicy policy = RetryPolicy.builder().maxAttempts(3).build();
    RuntimeException failure = new IllegalStateException("conflict");

    assertThat(policy.shouldRetry(failure, 1)).isTrue();
    assertThat(policy.shouldRetry(failure, 2)).isTrue();
    assertThat(policy.shouldRetry(failure, 3)).isFalse();
    assertThat(policy.shouldRetry(new IllegalArgumentException("bad"), 1)).isFalse();
  }

  @Test
  void noRetryNeverRetries() {
    RetryPolicy policy = RetryPolicy.noRetry();

    assertThat(policy.maxAttempts()).isEqualTo(1);
    assertThat(policy.shouldRetry(new IllegalStateException("conflict"), 1)).isFalse();
  }

  @Test
  void builderReplacesRetriableAndAddsNonRetriable() {
    BackoffStrategy backoff = BackoffStrategy.fixed(Duration.ofMillis(10));
    RetryPolicy policy =
        RetryPolicy.builder()
            .maxAttempts(5)
            .backoff(backoff)
            .retryOn(IllegalStateException.class, UncheckedIOException.class)
            .noRetryOn(UnsupportedOperationException.class)
            .build();

    assertThat(policy.maxAttempts()).isEqualTo(5);
    assertThat(policy.backoff()).isSameAs(backoff);
    assertThat(policy.retriableExceptions())
        .containsExactlyInAnyOrder(IllegalStateException.class, UncheckedIOException.class);
    assertThat(policy.nonRetriableExceptions())
        .contains(UnsupportedOperationException.class, IllegalArgumentException.class)
        .hasSize(6);
    assertThat(policy.isRetriable(new RuntimeException("no longer retriable"))).isFalse();
  }

  @Test
  void builderAcceptsCollections() {
    List<Class<? extends Throwable>> retriable = new ArrayList<>(List.of(IOException.class));
    RetryPolicy policy =
        RetryPolicy.builder()
            .retryOn(retriable)
            .noRetryOn(List.of(java.io.FileNotFoundException.class))
            .build();

    assertThat(policy.isRetriable(new IOException("io"))).isTrue();
    assertThat(policy.isRetriable(new java.io.FileNotFoundException("missing"))).isFalse();
  }

  @Test
  void setsAreImmutableCopies() {
    RetryPolicy policy = RetryPolicy.defaults();

    assertThatThrownBy(() -> policy.retriableExceptions().add(Exception.class))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> policy.nonRetriableExceptions().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsInvalidArguments() {
    BackoffStrategy backoff = BackoffStrategy.defaults();
    Set<Class<? extends Throwable>> types = Set.of(RuntimeException.class);

    assertThatThrownBy(() -> new RetryPolicy(0, backoff, types, types))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxAttempts must be at least 1");
    assertThatThrownBy(() -> new RetryPolicy(1, null, types, types))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("backoff");
    assertThatThrownBy(() -> new RetryPolicy(1, backoff, null, types))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retriableExceptions");
    assertThatThrownBy(() -> new RetryPolicy(1, backoff, types, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nonRetriableExceptions");
    assertThatThrownBy(() -> RetryPolicy.builder().maxAttempts(0).build())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
