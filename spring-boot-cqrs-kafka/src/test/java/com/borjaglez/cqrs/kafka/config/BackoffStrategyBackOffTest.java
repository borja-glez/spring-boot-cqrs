package com.borjaglez.cqrs.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOffExecution;

import com.borjaglez.cqrs.retry.BackoffStrategy;

class BackoffStrategyBackOffTest {

  private final BackoffStrategy strategy =
      BackoffStrategy.exponential(Duration.ofSeconds(1), 2.0, Duration.ofSeconds(10));

  @Test
  void waitsTheStrategyDelayBetweenAttemptsAndStopsAfterTheLastOne() {
    BackOffExecution execution = new BackoffStrategyBackOff(strategy, 3).start();

    assertThat(execution.nextBackOff()).isEqualTo(1000L);
    assertThat(execution.nextBackOff()).isEqualTo(2000L);
    assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
  }

  @Test
  void capsTheDelayAtTheMaximum() {
    BackOffExecution execution = new BackoffStrategyBackOff(strategy, 10).start();
    long last = 0;
    for (int i = 0; i < 9; i++) {
      last = execution.nextBackOff();
    }

    assertThat(last).isEqualTo(10_000L);
    assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
  }

  @Test
  void singleAttemptNeverRetries() {
    assertThat(new BackoffStrategyBackOff(strategy, 1).start().nextBackOff())
        .isEqualTo(BackOffExecution.STOP);
  }

  @Test
  void everyExecutionCountsItsOwnAttempts() {
    BackoffStrategyBackOff backOff = new BackoffStrategyBackOff(strategy, 2);
    BackOffExecution first = backOff.start();
    first.nextBackOff();

    assertThat(backOff.start().nextBackOff()).isEqualTo(1000L);
  }
}
