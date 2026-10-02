package com.borjaglez.cqrs.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link IdempotencyStore} kept in this process's memory. Suitable for tests and single-instance
 * applications: markers are not shared between instances and are lost on restart, and a crash
 * between the handler's effect and {@link #complete} lets a redelivery apply the effect again.
 *
 * <p>An acquired message blocks duplicates for {@code lease}; a completed one for {@code
 * retention}. Expired entries are removed when touched and by a sweep every {@value #SWEEP_EVERY}
 * acquisitions.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

  static final int SWEEP_EVERY = 1_000;

  private record Key(String handlerId, String messageId) {}

  private final ConcurrentHashMap<Key, Instant> expiries = new ConcurrentHashMap<>();
  private final AtomicInteger acquisitions = new AtomicInteger();
  private final Duration retention;
  private final Duration lease;
  private final Clock clock;

  public InMemoryIdempotencyStore(Duration retention, Duration lease) {
    this(retention, lease, Clock.systemUTC());
  }

  public InMemoryIdempotencyStore(Duration retention, Duration lease, Clock clock) {
    this.retention = positive("retention", retention);
    this.lease = positive("lease", lease);
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public Acquisition tryAcquire(String handlerId, String messageId) {
    sweepPeriodically();
    Instant now = clock.instant();
    boolean[] acquired = {false};
    expiries.compute(
        new Key(handlerId, messageId),
        (key, expiry) -> {
          if (expiry != null && expiry.isAfter(now)) {
            return expiry;
          }
          acquired[0] = true;
          return now.plus(lease);
        });
    return acquired[0] ? Acquisition.ACQUIRED : Acquisition.DUPLICATE;
  }

  @Override
  public void complete(String handlerId, String messageId) {
    expiries.put(new Key(handlerId, messageId), clock.instant().plus(retention));
  }

  @Override
  public void release(String handlerId, String messageId) {
    expiries.remove(new Key(handlerId, messageId));
  }

  int size() {
    return expiries.size();
  }

  private void sweepPeriodically() {
    if (acquisitions.incrementAndGet() % SWEEP_EVERY == 0) {
      Instant now = clock.instant();
      expiries.values().removeIf(expiry -> !expiry.isAfter(now));
    }
  }

  private static Duration positive(String name, Duration value) {
    if (value == null || value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException(name + " must be positive: " + value);
    }
    return value;
  }
}
