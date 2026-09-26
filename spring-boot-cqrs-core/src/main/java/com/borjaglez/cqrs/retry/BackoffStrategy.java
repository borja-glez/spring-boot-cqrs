package com.borjaglez.cqrs.retry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Computes how long to wait before the next attempt of an operation that failed.
 *
 * <p>The strategy is stateless and independent of the bus, so any retry loop can reuse it: {@link
 * RetryMiddleware}, a transport error handler or a persistent saga step. {@code failedAttempts} is
 * the number of attempts that have failed so far, starting at {@code 1}: the delay returned for
 * {@code 1} is the wait between the first and the second attempt.
 */
public sealed interface BackoffStrategy
    permits BackoffStrategy.Fixed,
        BackoffStrategy.Exponential,
        BackoffStrategy.ExponentialWithJitter {

  /** Default first delay of {@link #defaults()}: 100 ms. */
  Duration DEFAULT_INITIAL_DELAY = Duration.ofMillis(100);

  /** Default growth factor of {@link #defaults()}: 2.0. */
  double DEFAULT_MULTIPLIER = 2.0;

  /** Default upper bound of {@link #defaults()}: 5 s. */
  Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(5);

  /** Default jitter of {@link #defaults()}: 0.1, i.e. +/-10 %. */
  double DEFAULT_JITTER_FACTOR = 0.1;

  /**
   * Returns the delay to wait after {@code failedAttempts} failed attempts.
   *
   * @param failedAttempts attempts that have failed so far, at least {@code 1}
   * @param random source of randomness for strategies with jitter; ignored by the others
   * @throws IllegalArgumentException if {@code failedAttempts} is lower than {@code 1}
   */
  Duration delayAfter(int failedAttempts, RandomGenerator random);

  /**
   * Returns the delay to wait after {@code failedAttempts} failed attempts, drawing any jitter from
   * {@link ThreadLocalRandom}.
   */
  default Duration delayAfter(int failedAttempts) {
    return delayAfter(failedAttempts, ThreadLocalRandom.current());
  }

  /** Waits {@code delay} before every retry. */
  static BackoffStrategy fixed(Duration delay) {
    return new Fixed(delay);
  }

  /**
   * Waits {@code min(maxDelay, initialDelay * multiplier^(failedAttempts - 1))} before every retry.
   */
  static BackoffStrategy exponential(Duration initialDelay, double multiplier, Duration maxDelay) {
    return new Exponential(initialDelay, multiplier, maxDelay);
  }

  /**
   * Like {@link #exponential}, with the delay scaled by a random factor in {@code [1 -
   * jitterFactor, 1 + jitterFactor]} and capped at {@code maxDelay}. Jitter spreads the retries of
   * concurrent callers that failed together.
   */
  static BackoffStrategy exponentialWithJitter(
      Duration initialDelay, double multiplier, Duration maxDelay, double jitterFactor) {
    return new ExponentialWithJitter(initialDelay, multiplier, maxDelay, jitterFactor);
  }

  /** Exponential with jitter: 100 ms, multiplier 2.0, capped at 5 s, jitter factor 0.1. */
  static BackoffStrategy defaults() {
    return exponentialWithJitter(
        DEFAULT_INITIAL_DELAY, DEFAULT_MULTIPLIER, DEFAULT_MAX_DELAY, DEFAULT_JITTER_FACTOR);
  }

  /** Constant delay between attempts. */
  record Fixed(Duration delay) implements BackoffStrategy {

    public Fixed {
      requireNonNegative(delay, "delay");
    }

    @Override
    public Duration delayAfter(int failedAttempts, RandomGenerator random) {
      requireFailedAttempts(failedAttempts);
      return delay;
    }
  }

  /** Delay multiplied by {@code multiplier} after every failed attempt, capped at {@code max}. */
  record Exponential(Duration initialDelay, double multiplier, Duration maxDelay)
      implements BackoffStrategy {

    public Exponential {
      requireExponential(initialDelay, multiplier, maxDelay);
    }

    @Override
    public Duration delayAfter(int failedAttempts, RandomGenerator random) {
      requireFailedAttempts(failedAttempts);
      return Duration.ofNanos(exponentialNanos(initialDelay, multiplier, maxDelay, failedAttempts));
    }
  }

  /** {@link Exponential} scaled by a random factor around {@code 1}, capped at {@code max}. */
  record ExponentialWithJitter(
      Duration initialDelay, double multiplier, Duration maxDelay, double jitterFactor)
      implements BackoffStrategy {

    public ExponentialWithJitter {
      requireExponential(initialDelay, multiplier, maxDelay);
      if (!(jitterFactor >= 0 && jitterFactor <= 1)) {
        throw new IllegalArgumentException(
            "jitterFactor must be between 0 and 1, was " + jitterFactor);
      }
    }

    @Override
    public Duration delayAfter(int failedAttempts, RandomGenerator random) {
      requireFailedAttempts(failedAttempts);
      double base = exponentialNanos(initialDelay, multiplier, maxDelay, failedAttempts);
      double factor = 1 + jitterFactor * (2 * random.nextDouble() - 1);
      return Duration.ofNanos((long) Math.min(maxDelay.toNanos(), base * factor));
    }
  }

  private static void requireExponential(
      Duration initialDelay, double multiplier, Duration maxDelay) {
    requireNonNegative(initialDelay, "initialDelay");
    requireNonNegative(maxDelay, "maxDelay");
    if (!(multiplier >= 1)) {
      throw new IllegalArgumentException("multiplier must be at least 1, was " + multiplier);
    }
  }

  private static void requireNonNegative(Duration value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    if (value.isNegative()) {
      throw new IllegalArgumentException(name + " must not be negative, was " + value);
    }
  }

  private static void requireFailedAttempts(int failedAttempts) {
    if (failedAttempts < 1) {
      throw new IllegalArgumentException(
          "failedAttempts must be at least 1, was " + failedAttempts);
    }
  }

  private static long exponentialNanos(
      Duration initialDelay, double multiplier, Duration maxDelay, int failedAttempts) {
    double nanos = initialDelay.toNanos() * Math.pow(multiplier, failedAttempts - 1);
    return (long) Math.min(maxDelay.toNanos(), nanos);
  }
}
