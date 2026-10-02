package com.borjaglez.cqrs.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class InMemoryIdempotencyStoreTest {

  private static final Duration RETENTION = Duration.ofDays(7);
  private static final Duration LEASE = Duration.ofMinutes(5);

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-02T10:00:00Z"));
  private final InMemoryIdempotencyStore store =
      new InMemoryIdempotencyStore(RETENTION, LEASE, clock);

  @Test
  void firstAcquisitionWinsAndACompletedMessageIsADuplicate() {
    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
    store.complete("h", "m");

    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.DUPLICATE);
    assertThat(store.tryAcquire("other", "m")).isEqualTo(Acquisition.ACQUIRED);
    assertThat(store.tryAcquire("h", "other")).isEqualTo(Acquisition.ACQUIRED);
  }

  @Test
  void aReleasedMessageCanBeAcquiredAgain() {
    store.tryAcquire("h", "m");
    store.release("h", "m");

    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
  }

  @Test
  void anInProgressMessageIsADuplicateUntilItsLeaseExpires() {
    store.tryAcquire("h", "m");
    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.DUPLICATE);

    clock.advance(LEASE);

    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
  }

  @Test
  void aCompletedMessageIsForgottenAfterTheRetention() {
    store.tryAcquire("h", "m");
    store.complete("h", "m");

    clock.advance(RETENTION.minusSeconds(1));
    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.DUPLICATE);

    clock.advance(Duration.ofSeconds(1));
    assertThat(store.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
  }

  @Test
  void expiredEntriesAreSweptPeriodically() {
    for (int i = 0; i < 10; i++) {
      store.tryAcquire("h", "old-" + i);
      store.complete("h", "old-" + i);
    }
    clock.advance(RETENTION);
    int acquisitionsSoFar = 10;
    for (int i = acquisitionsSoFar; i < InMemoryIdempotencyStore.SWEEP_EVERY - 1; i++) {
      store.tryAcquire("h", "new-" + i);
    }
    assertThat(store.size()).isEqualTo(InMemoryIdempotencyStore.SWEEP_EVERY - 1);

    store.tryAcquire("h", "last");

    assertThat(store.size()).isEqualTo(InMemoryIdempotencyStore.SWEEP_EVERY - 10);
  }

  @Test
  void concurrentDeliveriesApplyTheEffectOnce() throws Exception {
    IdempotentInvoker invoker = new IdempotentInvoker(store);
    AtomicInteger applied = new AtomicInteger();
    int threads = 16;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  return invoker.invoke("h", "m", applied::incrementAndGet);
                }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(10, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(applied).hasValue(1);
  }

  @Test
  void rejectsNonPositiveDurations() {
    assertThatThrownBy(() -> new InMemoryIdempotencyStore(Duration.ZERO, LEASE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("retention must be positive: PT0S");
    assertThatThrownBy(() -> new InMemoryIdempotencyStore(RETENTION, Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("lease must be positive: PT-1S");
    assertThatThrownBy(() -> new InMemoryIdempotencyStore(null, LEASE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("retention must be positive: null");
  }

  @Test
  void usesTheSystemClockByDefault() {
    InMemoryIdempotencyStore systemStore = new InMemoryIdempotencyStore(RETENTION, LEASE);

    assertThat(systemStore.tryAcquire("h", "m")).isEqualTo(Acquisition.ACQUIRED);
  }

  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
