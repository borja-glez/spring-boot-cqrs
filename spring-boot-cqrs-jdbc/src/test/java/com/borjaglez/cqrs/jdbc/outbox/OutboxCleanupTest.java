package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class OutboxCleanupTest {

  private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

  private final OutboxStore store = mock(OutboxStore.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final OutboxCleanup cleanup =
      new OutboxCleanup(store, Duration.ofDays(7), Duration.ofMillis(50), clock);

  @Test
  void purgeDeletesRowsPublishedBeforeTheRetention() {
    when(store.deletePublishedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(4);

    assertThat(cleanup.purge()).isEqualTo(4);
    assertThat(cleanup.retention()).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void purgeSurvivesDatabaseErrors() {
    when(store.deletePublishedBefore(any())).thenThrow(new IllegalStateException("db down"));

    assertThat(cleanup.purge()).isZero();
  }

  @Test
  void runsPeriodicallyWhileStarted() {
    cleanup.start();
    cleanup.start(); // idempotent
    try {
      assertThat(cleanup.isRunning()).isTrue();
      verify(store, timeout(5000).atLeast(2)).deletePublishedBefore(any());
    } finally {
      cleanup.stop();
    }
    cleanup.stop(); // idempotent

    assertThat(cleanup.isRunning()).isFalse();
  }

  @Test
  void rejectsNonPositiveRetentionAndInterval() {
    assertThatThrownBy(() -> new OutboxCleanup(store, Duration.ZERO, Duration.ofMinutes(1), clock))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retention");
    assertThatThrownBy(
            () -> new OutboxCleanup(store, Duration.ofDays(-1), Duration.ofMinutes(1), clock))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OutboxCleanup(store, Duration.ofDays(1), Duration.ZERO, clock))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("interval");
  }
}
