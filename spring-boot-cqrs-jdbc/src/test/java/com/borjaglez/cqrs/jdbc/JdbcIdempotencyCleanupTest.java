package com.borjaglez.cqrs.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class JdbcIdempotencyCleanupTest {

  private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

  private final JdbcIdempotencyStore store = mock(JdbcIdempotencyStore.class);
  private final JdbcIdempotencyCleanup cleanup =
      new JdbcIdempotencyCleanup(
          store, Duration.ofDays(7), Duration.ofMillis(50), Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void purgeDeletesMarkersOlderThanTheRetention() {
    when(store.deleteProcessedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(3);

    assertThat(cleanup.purge()).isEqualTo(3);
  }

  @Test
  void purgeSurvivesDatabaseErrors() {
    when(store.deleteProcessedBefore(any())).thenThrow(new IllegalStateException("db down"));

    assertThat(cleanup.purge()).isZero();
  }

  @Test
  void runsPeriodicallyWhileStarted() {
    assertThat(cleanup.isRunning()).isFalse();

    cleanup.start();
    cleanup.start(); // idempotent
    try {
      assertThat(cleanup.isRunning()).isTrue();
      await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> verify(store, atLeast(2)).deleteProcessedBefore(any()));
    } finally {
      cleanup.stop();
    }
    cleanup.stop(); // idempotent

    assertThat(cleanup.isRunning()).isFalse();
  }

  @Test
  void rejectsNonPositiveRetentionAndInterval() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    assertThatThrownBy(
            () -> new JdbcIdempotencyCleanup(store, Duration.ZERO, Duration.ofMinutes(1), clock))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retention");
    assertThatThrownBy(
            () ->
                new JdbcIdempotencyCleanup(
                    store, Duration.ofDays(1), Duration.ofMinutes(-1), clock))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("interval");
    assertThatThrownBy(
            () ->
                new JdbcIdempotencyCleanup(
                    store, Duration.ofDays(-1), Duration.ofMinutes(1), clock))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
