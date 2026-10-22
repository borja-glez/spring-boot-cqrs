package com.borjaglez.cqrs.jdbc.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class OutboxMetricsTest {

  private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");

  /** A clock the test moves by hand. */
  private static final class MutableClock extends Clock {
    private Instant now = NOW;

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
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

  private final OutboxStore store = mock(OutboxStore.class);
  private final MutableClock clock = new MutableClock();
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final OutboxMetrics metrics = new OutboxMetrics(store, clock);

  private double gauge(String name) {
    return registry.get(name).gauge().value();
  }

  @Test
  void gaugesReflectTheStore() {
    when(store.stats()).thenReturn(new OutboxStats(7, 2, NOW.minusSeconds(30)));
    metrics.bindTo(registry);

    assertThat(gauge("cqrs.outbox.pending")).isEqualTo(7);
    assertThat(gauge("cqrs.outbox.set.aside")).isEqualTo(2);

    clock.advance(Duration.ofSeconds(5));
    when(store.stats()).thenReturn(new OutboxStats(1, 0, null));

    assertThat(gauge("cqrs.outbox.pending")).isEqualTo(1);
    assertThat(gauge("cqrs.outbox.set.aside")).isZero();
    assertThat(gauge("cqrs.outbox.oldest.pending.age")).isZero();
  }

  @Test
  void oldestPendingAgeUsesTheClock() {
    when(store.stats()).thenReturn(new OutboxStats(1, 0, NOW.minusSeconds(90)));
    metrics.bindTo(registry);

    assertThat(registry.get("cqrs.outbox.oldest.pending.age").timeGauge().baseTimeUnit())
        .isEqualTo(TimeUnit.SECONDS);
    assertThat(gauge("cqrs.outbox.oldest.pending.age")).isEqualTo(90);

    clock.advance(Duration.ofSeconds(30));

    assertThat(gauge("cqrs.outbox.oldest.pending.age")).isEqualTo(120);
  }

  @Test
  void ageNeverGoesNegative() {
    when(store.stats()).thenReturn(new OutboxStats(1, 0, NOW.plusSeconds(60)));
    metrics.bindTo(registry);

    assertThat(gauge("cqrs.outbox.oldest.pending.age")).isZero();
  }

  @Test
  void theGaugesShareOneStoreReadPerScrape() {
    when(store.stats()).thenReturn(new OutboxStats(3, 1, NOW.minusSeconds(10)));
    metrics.bindTo(registry);

    gauge("cqrs.outbox.pending");
    gauge("cqrs.outbox.set.aside");
    gauge("cqrs.outbox.oldest.pending.age");

    verify(store, times(1)).stats();

    clock.advance(Duration.ofMillis(OutboxMetrics.CACHE_MILLIS));
    gauge("cqrs.outbox.pending");
    gauge("cqrs.outbox.set.aside");

    verify(store, times(2)).stats();
  }

  @Test
  void countersFollowTheListener() {
    metrics.bindTo(registry);

    metrics.published(3);
    metrics.published(2);
    metrics.publishFailed();
    metrics.setAside(4);

    assertThat(registry.get("cqrs.outbox.published").counter().count()).isEqualTo(5);
    assertThat(registry.get("cqrs.outbox.publish.failures").counter().count()).isEqualTo(1);
  }

  @Test
  void outcomesBeforeTheMetersAreBoundAreIgnored() {
    metrics.published(3);
    metrics.publishFailed();

    metrics.bindTo(registry);

    assertThat(registry.get("cqrs.outbox.published").counter().count()).isZero();
    assertThat(registry.get("cqrs.outbox.publish.failures").counter().count()).isZero();
  }
}
