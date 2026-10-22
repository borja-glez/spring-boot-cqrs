package com.borjaglez.cqrs.jdbc.outbox;

/**
 * Observes the outcome of relay batches. Callbacks run after the batch transaction committed, never
 * for a batch that rolled back, and only when there is something to report.
 */
public interface OutboxRelayListener {

  /** A listener that ignores everything. */
  static OutboxRelayListener noop() {
    return new OutboxRelayListener() {};
  }

  /** {@code count} rows were published and committed as published. */
  default void published(int count) {}

  /** The target bus failed to publish a row; it stays pending and is retried. */
  default void publishFailed() {}

  /** {@code count} unreadable rows were set aside. */
  default void setAside(int count) {}
}
