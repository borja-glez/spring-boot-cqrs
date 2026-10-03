package com.borjaglez.cqrs.jdbc.outbox;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs the {@link OutboxRelay} on one daemon thread: a first batch at start, then one every {@code
 * interval} after the previous run ended. While batches come back full and unbroken it runs the
 * next one right away. Nothing thrown by a run, not even an {@link Error}, stops the schedule. As a
 * {@link SmartLifecycle} in the last phase it stops before the data source closes, waiting a
 * bounded time for the batch in progress.
 */
public class OutboxRelayScheduler implements SmartLifecycle {

  private static final Log LOG = LogFactory.getLog(OutboxRelayScheduler.class);

  static final Duration DEFAULT_STOP_TIMEOUT = Duration.ofSeconds(10);

  private final OutboxRelay relay;
  private final Duration interval;
  private final Duration stopTimeout;
  private ScheduledExecutorService executor;
  private volatile boolean running;

  public OutboxRelayScheduler(OutboxRelay relay, Duration interval) {
    this(relay, interval, DEFAULT_STOP_TIMEOUT);
  }

  OutboxRelayScheduler(OutboxRelay relay, Duration interval, Duration stopTimeout) {
    if (interval.toMillis() <= 0) {
      throw new IllegalArgumentException(
          "cqrs.outbox.relay.interval must be at least 1 ms: " + interval);
    }
    this.relay = relay;
    this.interval = interval;
    this.stopTimeout = stopTimeout;
  }

  void runOnce() {
    try {
      OutboxRelay.BatchResult result;
      do {
        result = relay.relayBatch();
      } while (running && result.drainAgain());
    } catch (Throwable e) {
      // A task that throws is cancelled by the executor: log it and keep the schedule alive.
      LOG.error("Outbox relay run failed; retrying in " + interval, e);
    }
  }

  @Override
  public synchronized void start() {
    if (executor != null) {
      return;
    }
    running = true;
    executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "cqrs-outbox-relay");
              thread.setDaemon(true);
              return thread;
            });
    executor.scheduleWithFixedDelay(this::runOnce, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public synchronized void stop() {
    if (executor == null) {
      return;
    }
    running = false;
    executor.shutdown();
    try {
      if (!executor.awaitTermination(stopTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
        LOG.warn(
            "Outbox relay did not finish its batch within " + stopTimeout + "; interrupting it");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
    executor = null;
  }

  @Override
  public synchronized boolean isRunning() {
    return executor != null;
  }
}
