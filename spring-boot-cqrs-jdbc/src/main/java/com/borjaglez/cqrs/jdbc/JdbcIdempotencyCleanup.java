package com.borjaglez.cqrs.jdbc;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Deletes processed-message markers older than the retention every {@code interval}, on its own
 * daemon thread. Several instances may run it at once: the delete is idempotent.
 */
public class JdbcIdempotencyCleanup implements SmartLifecycle {

  private static final Log LOG = LogFactory.getLog(JdbcIdempotencyCleanup.class);

  private final JdbcIdempotencyStore store;
  private final Duration retention;
  private final Duration interval;
  private final Clock clock;
  private ScheduledExecutorService executor;

  public JdbcIdempotencyCleanup(
      JdbcIdempotencyStore store, Duration retention, Duration interval, Clock clock) {
    if (retention.isZero() || retention.isNegative()) {
      throw new IllegalArgumentException("retention must be positive: " + retention);
    }
    if (interval.toMillis() <= 0) {
      throw new IllegalArgumentException("interval must be at least 1 ms: " + interval);
    }
    this.store = store;
    this.retention = retention;
    this.interval = interval;
    this.clock = clock;
  }

  public Duration retention() {
    return retention;
  }

  public int purge() {
    try {
      return store.deleteProcessedBefore(clock.instant().minus(retention));
    } catch (RuntimeException e) {
      LOG.warn("Could not delete expired idempotency markers; retrying in " + interval, e);
      return 0;
    }
  }

  @Override
  public synchronized void start() {
    if (executor != null) {
      return;
    }
    executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "cqrs-idempotency-cleanup");
              thread.setDaemon(true);
              return thread;
            });
    long millis = interval.toMillis();
    executor.scheduleWithFixedDelay(this::purge, millis, millis, TimeUnit.MILLISECONDS);
  }

  @Override
  public synchronized void stop() {
    if (executor != null) {
      executor.shutdownNow();
      executor = null;
    }
  }

  @Override
  public synchronized boolean isRunning() {
    return executor != null;
  }
}
