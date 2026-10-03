package com.borjaglez.cqrs.jdbc.outbox;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.serialization.MessageSerializer;

/**
 * Publishes pending outbox rows through the target {@link EventBus}, one batch per transaction. The
 * batch is locked with {@code FOR UPDATE SKIP LOCKED}, so relays on other instances skip it. A row
 * is marked published only after the target bus returned.
 *
 * <p>The batch stops at the first failure so later rows do not overtake the failing one. A row that
 * cannot be decoded (its class is unknown or cannot be linked here, or its payload or stored
 * context does not deserialize) is set aside after {@code maxAttempts} attempts so the rest of the
 * outbox keeps moving. A failure of the target bus (broker down, timeout, rejection) is retried
 * forever: an outage must not park or reorder events.
 */
public class OutboxRelay {

  private static final Log LOG = LogFactory.getLog(OutboxRelay.class);

  private final OutboxStore store;
  private final OutboxEventTypeResolver resolver;
  private final MessageSerializer serializer;
  private final OutboxContextCodec contextCodec;
  private final EventBus target;
  private final TransactionTemplate transaction;
  private final int batchSize;
  private final int maxAttempts;

  public OutboxRelay(
      OutboxStore store,
      OutboxEventTypeResolver resolver,
      MessageSerializer serializer,
      OutboxContextCodec contextCodec,
      EventBus target,
      PlatformTransactionManager transactionManager,
      int batchSize,
      int maxAttempts) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("cqrs.outbox.relay.batch-size must be at least 1");
    }
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("cqrs.outbox.relay.max-attempts must be at least 1");
    }
    this.store = store;
    this.resolver = resolver;
    this.serializer = serializer;
    this.contextCodec = contextCodec;
    this.target = target;
    this.transaction = new TransactionTemplate(transactionManager);
    this.batchSize = batchSize;
    this.maxAttempts = maxAttempts;
  }

  public EventBus target() {
    return target;
  }

  /** Relays one batch in its own transaction. */
  public BatchResult relayBatch() {
    return transaction.execute(status -> relay(store.lockBatch(batchSize)));
  }

  private BatchResult relay(List<OutboxRecord> rows) {
    boolean full = rows.size() == batchSize;
    int published = 0;
    int setAside = 0;
    for (OutboxRecord row : rows) {
      Decoded decoded;
      try {
        decoded = decode(row);
      } catch (RuntimeException | LinkageError e) {
        // A class that cannot be linked here (NoClassDefFoundError, a newer class file version)
        // is as unreadable as an unknown one; other errors still roll the batch back.
        int attempts = row.attempts() + 1;
        if (attempts >= maxAttempts) {
          store.markFailed(row.id(), attempts, describe(e), true);
          LOG.error(
              "Outbox event '"
                  + row.eventName()
                  + "' ("
                  + row.eventId()
                  + ") could not be read "
                  + attempts
                  + " times and was set aside; later events continue. Clear its failed_at to"
                  + " retry it",
              e);
          setAside++;
          continue;
        }
        store.markFailed(row.id(), attempts, describe(e), false);
        LOG.warn(
            "Could not read outbox event '"
                + row.eventName()
                + "' ("
                + row.eventId()
                + "), attempt "
                + attempts
                + " of "
                + maxAttempts
                + "; the batch stops here",
            e);
        return new BatchResult(rows.size(), published, setAside, true, full);
      }
      try {
        contextCodec.runWithin(decoded.context(), () -> target.publish(decoded.event()));
      } catch (RuntimeException e) {
        store.markFailed(row.id(), row.attempts() + 1, describe(e), false);
        LOG.warn(
            "Could not publish outbox event '"
                + row.eventName()
                + "' ("
                + row.eventId()
                + "); the batch stops here and is retried",
            e);
        return new BatchResult(rows.size(), published, setAside, true, full);
      }
      store.markPublished(row.id());
      published++;
    }
    return new BatchResult(rows.size(), published, setAside, false, full);
  }

  private Decoded decode(OutboxRecord row) {
    Class<? extends Event> type = resolver.resolve(row.eventName(), row.eventClass());
    Event event = serializer.deserialize(row.payload(), type);
    return new Decoded(event, contextCodec.decode(row.context()));
  }

  private static String describe(Throwable e) {
    String type = e.getClass().getName();
    return e.getMessage() == null ? type : type + ": " + e.getMessage();
  }

  private record Decoded(Event event, OutboxContextCodec.Captured context) {}

  /**
   * Outcome of one batch: rows locked, published and set aside, whether a failure stopped it, and
   * whether it was full (more rows are probably waiting).
   */
  public record BatchResult(
      int locked, int published, int setAside, boolean stopped, boolean full) {

    /** Whether to run another batch right away: this one was full and nothing stopped it. */
    public boolean drainAgain() {
      return full && !stopped;
    }
  }
}
