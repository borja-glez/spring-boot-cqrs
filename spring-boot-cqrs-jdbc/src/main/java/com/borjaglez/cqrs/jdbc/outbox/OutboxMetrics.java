package com.borjaglez.cqrs.jdbc.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Micrometer meters of the outbox: {@code cqrs.outbox.pending}, {@code cqrs.outbox.set.aside},
 * {@code cqrs.outbox.oldest.pending.age} (seconds, 0 without pending rows), {@code
 * cqrs.outbox.published} and {@code cqrs.outbox.publish.failures}. The gauges share one {@link
 * OutboxStore#stats()} read per scrape: a read is reused for {@value #CACHE_MILLIS} ms. Outcomes
 * reported before the meters are bound are not counted.
 */
public class OutboxMetrics implements MeterBinder, OutboxRelayListener {

  static final long CACHE_MILLIS = 1000;

  private final OutboxStore store;
  private final Clock clock;
  private volatile Counter published;
  private volatile Counter failures;
  private Snapshot snapshot;

  public OutboxMetrics(OutboxStore store, Clock clock) {
    this.store = Objects.requireNonNull(store, "store must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("cqrs.outbox.pending", this, m -> m.stats().pending())
        .description("Outbox rows waiting to be relayed")
        .register(registry);
    Gauge.builder("cqrs.outbox.set.aside", this, m -> m.stats().setAside())
        .description("Outbox rows set aside after repeated unreadable attempts")
        .register(registry);
    TimeGauge.builder("cqrs.outbox.oldest.pending.age", this, TimeUnit.SECONDS, m -> m.oldestAge())
        .description("Age of the oldest pending outbox row")
        .register(registry);
    published =
        Counter.builder("cqrs.outbox.published")
            .description("Outbox rows published by this instance")
            .register(registry);
    failures =
        Counter.builder("cqrs.outbox.publish.failures")
            .description("Failed attempts to publish an outbox row")
            .register(registry);
  }

  @Override
  public void published(int count) {
    Counter counter = published;
    if (counter != null) {
      counter.increment(count);
    }
  }

  @Override
  public void publishFailed() {
    Counter counter = failures;
    if (counter != null) {
      counter.increment();
    }
  }

  private double oldestAge() {
    Instant oldest = stats().oldestPendingCreatedAt();
    if (oldest == null) {
      return 0;
    }
    return Math.max(0, Duration.between(oldest, clock.instant()).toMillis() / 1000.0);
  }

  private synchronized OutboxStats stats() {
    long now = clock.millis();
    if (snapshot == null || Math.abs(now - snapshot.readAt()) >= CACHE_MILLIS) {
      snapshot = new Snapshot(store.stats(), now);
    }
    return snapshot.stats();
  }

  private record Snapshot(OutboxStats stats, long readAt) {}
}
