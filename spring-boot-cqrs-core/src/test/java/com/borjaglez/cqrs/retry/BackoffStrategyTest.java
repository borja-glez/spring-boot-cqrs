package com.borjaglez.cqrs.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

class BackoffStrategyTest {

  private static final RandomGenerator LOWEST = fixedRandom(0.0);
  private static final RandomGenerator MIDDLE = fixedRandom(0.5);
  private static final RandomGenerator HIGHEST = fixedRandom(0.999_999_999);

  @Test
  void fixedReturnsSameDelayForEveryAttempt() {
    BackoffStrategy strategy = BackoffStrategy.fixed(Duration.ofMillis(250));

    assertThat(strategy.delayAfter(1, LOWEST)).isEqualTo(Duration.ofMillis(250));
    assertThat(strategy.delayAfter(5, HIGHEST)).isEqualTo(Duration.ofMillis(250));
    assertThat(strategy).isEqualTo(new BackoffStrategy.Fixed(Duration.ofMillis(250)));
  }

  @Test
  void fixedAcceptsZeroDelay() {
    assertThat(BackoffStrategy.fixed(Duration.ZERO).delayAfter(1)).isEqualTo(Duration.ZERO);
  }

  @Test
  void fixedRejectsNullAndNegativeDelay() {
    assertThatThrownBy(() -> BackoffStrategy.fixed(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("delay");
    assertThatThrownBy(() -> BackoffStrategy.fixed(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("delay must not be negative");
  }

  @Test
  void exponentialMultipliesInitialDelayPerFailedAttempt() {
    BackoffStrategy strategy =
        BackoffStrategy.exponential(Duration.ofMillis(100), 2.0, Duration.ofSeconds(5));

    assertThat(strategy.delayAfter(1, LOWEST)).isEqualTo(Duration.ofMillis(100));
    assertThat(strategy.delayAfter(2, LOWEST)).isEqualTo(Duration.ofMillis(200));
    assertThat(strategy.delayAfter(3, LOWEST)).isEqualTo(Duration.ofMillis(400));
    assertThat(strategy.delayAfter(6, LOWEST)).isEqualTo(Duration.ofMillis(3200));
  }

  @Test
  void exponentialIsCappedAtMaxDelay() {
    BackoffStrategy strategy =
        BackoffStrategy.exponential(Duration.ofMillis(100), 2.0, Duration.ofSeconds(5));

    assertThat(strategy.delayAfter(7, LOWEST)).isEqualTo(Duration.ofSeconds(5));
    assertThat(strategy.delayAfter(1_000, LOWEST)).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void exponentialWithMultiplierOneIsFixed() {
    BackoffStrategy strategy =
        BackoffStrategy.exponential(Duration.ofMillis(100), 1.0, Duration.ofSeconds(5));

    assertThat(strategy.delayAfter(10)).isEqualTo(Duration.ofMillis(100));
  }

  @Test
  void exponentialRejectsInvalidArguments() {
    assertThatThrownBy(() -> BackoffStrategy.exponential(null, 2.0, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("initialDelay");
    assertThatThrownBy(
            () -> BackoffStrategy.exponential(Duration.ofMillis(-1), 2.0, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("initialDelay must not be negative");
    assertThatThrownBy(() -> BackoffStrategy.exponential(Duration.ofMillis(1), 2.0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxDelay");
    assertThatThrownBy(
            () -> BackoffStrategy.exponential(Duration.ofMillis(1), 2.0, Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxDelay must not be negative");
    assertThatThrownBy(
            () -> BackoffStrategy.exponential(Duration.ofMillis(1), 0.5, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("multiplier must be at least 1");
    assertThatThrownBy(
            () ->
                BackoffStrategy.exponential(
                    Duration.ofMillis(1), Double.NaN, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("multiplier");
  }

  @Test
  void exponentialWithJitterStaysWithinJitterBounds() {
    BackoffStrategy strategy =
        BackoffStrategy.exponentialWithJitter(
            Duration.ofMillis(100), 2.0, Duration.ofSeconds(5), 0.1);

    assertThat(strategy.delayAfter(1, LOWEST)).isEqualTo(Duration.ofMillis(90));
    assertThat(strategy.delayAfter(1, MIDDLE)).isEqualTo(Duration.ofMillis(100));
    assertThat(strategy.delayAfter(1, HIGHEST))
        .isGreaterThan(Duration.ofMillis(109))
        .isLessThanOrEqualTo(Duration.ofMillis(110));
    assertThat(strategy.delayAfter(3, LOWEST)).isEqualTo(Duration.ofMillis(360));
  }

  @Test
  void exponentialWithJitterIsCappedAtMaxDelay() {
    BackoffStrategy strategy =
        BackoffStrategy.exponentialWithJitter(
            Duration.ofMillis(100), 2.0, Duration.ofSeconds(5), 0.1);

    assertThat(strategy.delayAfter(20, HIGHEST)).isEqualTo(Duration.ofSeconds(5));
    assertThat(strategy.delayAfter(20, LOWEST)).isEqualTo(Duration.ofMillis(4500));
  }

  @Test
  void exponentialWithJitterUsesSharedRandomByDefault() {
    BackoffStrategy strategy =
        BackoffStrategy.exponentialWithJitter(
            Duration.ofMillis(100), 2.0, Duration.ofSeconds(5), 0.5);

    for (int i = 0; i < 100; i++) {
      assertThat(strategy.delayAfter(1))
          .isGreaterThanOrEqualTo(Duration.ofMillis(50))
          .isLessThanOrEqualTo(Duration.ofMillis(150));
    }
  }

  @Test
  void exponentialWithZeroJitterIsExponential() {
    BackoffStrategy strategy =
        BackoffStrategy.exponentialWithJitter(
            Duration.ofMillis(100), 2.0, Duration.ofSeconds(5), 0);

    assertThat(strategy.delayAfter(2, HIGHEST)).isEqualTo(Duration.ofMillis(200));
  }

  @Test
  void exponentialWithJitterRejectsInvalidArguments() {
    assertThatThrownBy(
            () ->
                BackoffStrategy.exponentialWithJitter(
                    Duration.ofMillis(-1), 2.0, Duration.ofSeconds(1), 0.1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("initialDelay");
    assertThatThrownBy(
            () ->
                BackoffStrategy.exponentialWithJitter(
                    Duration.ofMillis(1), 2.0, Duration.ofMillis(-1), 0.1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxDelay");
    assertThatThrownBy(
            () ->
                BackoffStrategy.exponentialWithJitter(
                    Duration.ofMillis(1), 0.9, Duration.ofSeconds(1), 0.1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("multiplier");
    assertThatThrownBy(
            () ->
                BackoffStrategy.exponentialWithJitter(
                    Duration.ofMillis(1), 2.0, Duration.ofSeconds(1), -0.1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jitterFactor must be between 0 and 1");
    assertThatThrownBy(
            () ->
                BackoffStrategy.exponentialWithJitter(
                    Duration.ofMillis(1), 2.0, Duration.ofSeconds(1), 1.1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jitterFactor");
  }

  @Test
  void defaultsAreExponentialWithJitter() {
    assertThat(BackoffStrategy.defaults())
        .isEqualTo(
            new BackoffStrategy.ExponentialWithJitter(
                BackoffStrategy.DEFAULT_INITIAL_DELAY,
                BackoffStrategy.DEFAULT_MULTIPLIER,
                BackoffStrategy.DEFAULT_MAX_DELAY,
                BackoffStrategy.DEFAULT_JITTER_FACTOR));
    assertThat(BackoffStrategy.DEFAULT_INITIAL_DELAY).isEqualTo(Duration.ofMillis(100));
    assertThat(BackoffStrategy.DEFAULT_MULTIPLIER).isEqualTo(2.0);
    assertThat(BackoffStrategy.DEFAULT_MAX_DELAY).isEqualTo(Duration.ofSeconds(5));
    assertThat(BackoffStrategy.DEFAULT_JITTER_FACTOR).isEqualTo(0.1);
  }

  @Test
  void delayRejectsAttemptBelowOne() {
    BackoffStrategy fixed = BackoffStrategy.fixed(Duration.ofMillis(1));
    BackoffStrategy exponential =
        BackoffStrategy.exponential(Duration.ofMillis(1), 2.0, Duration.ofSeconds(1));
    BackoffStrategy jitter = BackoffStrategy.defaults();

    assertThatThrownBy(() -> fixed.delayAfter(0, LOWEST))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("failedAttempts must be at least 1");
    assertThatThrownBy(() -> exponential.delayAfter(0, LOWEST))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> jitter.delayAfter(-1, LOWEST))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static RandomGenerator fixedRandom(double value) {
    return new RandomGenerator() {
      @Override
      public long nextLong() {
        return 0;
      }

      @Override
      public double nextDouble() {
        return value;
      }
    };
  }
}
