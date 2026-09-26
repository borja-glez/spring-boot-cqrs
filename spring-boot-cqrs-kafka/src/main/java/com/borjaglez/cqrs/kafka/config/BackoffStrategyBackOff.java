package com.borjaglez.cqrs.kafka.config;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import com.borjaglez.cqrs.retry.BackoffStrategy;

/**
 * A Spring {@link BackOff} driven by a {@link BackoffStrategy}: it allows {@code maxAttempts - 1}
 * retries, waiting {@code strategy.delayAfter(n)} after the {@code n}-th failed attempt.
 */
final class BackoffStrategyBackOff implements BackOff {

  private final BackoffStrategy strategy;
  private final int maxAttempts;

  BackoffStrategyBackOff(BackoffStrategy strategy, int maxAttempts) {
    this.strategy = strategy;
    this.maxAttempts = maxAttempts;
  }

  @Override
  public BackOffExecution start() {
    AtomicInteger failedAttempts = new AtomicInteger();
    return () -> {
      int failed = failedAttempts.incrementAndGet();
      return failed < maxAttempts ? strategy.delayAfter(failed).toMillis() : BackOffExecution.STOP;
    };
  }
}
