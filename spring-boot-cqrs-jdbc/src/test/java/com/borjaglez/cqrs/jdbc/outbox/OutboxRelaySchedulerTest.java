package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class OutboxRelaySchedulerTest {

  private static final OutboxRelay.BatchResult EMPTY =
      new OutboxRelay.BatchResult(0, 0, 0, false, false);
  private static final OutboxRelay.BatchResult FULL =
      new OutboxRelay.BatchResult(2, 2, 0, false, true);

  private final OutboxRelay relay = mock(OutboxRelay.class);

  @Test
  void runsTheRelayWhileStarted() {
    when(relay.relayBatch()).thenReturn(EMPTY);
    OutboxRelayScheduler scheduler = new OutboxRelayScheduler(relay, Duration.ofMillis(10));

    assertThat(scheduler.isRunning()).isFalse();
    scheduler.start();
    scheduler.start(); // idempotent
    try {
      assertThat(scheduler.isRunning()).isTrue();
      verify(relay, timeout(5000).atLeast(2)).relayBatch();
    } finally {
      scheduler.stop();
    }
    scheduler.stop(); // idempotent

    assertThat(scheduler.isRunning()).isFalse();
  }

  @Test
  void drainsRightAwayWhileBatchesComeBackFull() {
    when(relay.relayBatch()).thenReturn(FULL, FULL, EMPTY);
    OutboxRelayScheduler scheduler = new OutboxRelayScheduler(relay, Duration.ofHours(1));

    scheduler.start();
    try {
      verify(relay, timeout(5000).times(3)).relayBatch();
    } finally {
      scheduler.stop();
    }
  }

  @Test
  void runOnceWhenNotStartedRunsOneBatch() {
    when(relay.relayBatch()).thenReturn(FULL);

    new OutboxRelayScheduler(relay, Duration.ofSeconds(1)).runOnce();

    verify(relay, times(1)).relayBatch();
  }

  @Test
  void survivesExceptionsAndErrors() {
    when(relay.relayBatch())
        .thenThrow(new IllegalStateException("database down"))
        .thenThrow(new AssertionError("fatal"))
        .thenReturn(EMPTY);
    OutboxRelayScheduler scheduler = new OutboxRelayScheduler(relay, Duration.ofMillis(10));

    scheduler.start();
    try {
      verify(relay, timeout(5000).atLeast(3)).relayBatch();
    } finally {
      scheduler.stop();
    }
  }

  @Test
  void stopInterruptsABatchThatOutlivesTheTimeout() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(relay.relayBatch())
        .thenAnswer(
            invocation -> {
              entered.countDown();
              release.await();
              return EMPTY;
            });
    OutboxRelayScheduler scheduler =
        new OutboxRelayScheduler(relay, Duration.ofHours(1), Duration.ofMillis(50));

    scheduler.start();
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
    scheduler.stop();

    assertThat(scheduler.isRunning()).isFalse();
    release.countDown();
  }

  @Test
  void stopFromAnInterruptedThreadKeepsTheInterrupt() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(relay.relayBatch())
        .thenAnswer(
            invocation -> {
              entered.countDown();
              release.await();
              return EMPTY;
            });
    OutboxRelayScheduler scheduler = new OutboxRelayScheduler(relay, Duration.ofHours(1));
    scheduler.start();
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

    Thread.currentThread().interrupt();
    scheduler.stop();

    assertThat(Thread.interrupted()).isTrue();
    assertThat(scheduler.isRunning()).isFalse();
    release.countDown();
  }

  @Test
  void rejectsANonPositiveInterval() {
    assertThatThrownBy(() -> new OutboxRelayScheduler(relay, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("interval");
  }
}
