package com.borjaglez.cqrs.jdbc.outbox;

import java.util.List;

/**
 * Observes the outcome of relay batches. Callbacks run after the batch transaction committed, never
 * for a batch that rolled back, and only when there is something to report.
 */
public interface OutboxRelayListener {

  /** A listener that ignores everything. */
  static OutboxRelayListener noop() {
    return new OutboxRelayListener() {};
  }

  /**
   * One listener for all of them: the no-op when empty, otherwise a listener that calls each in
   * turn and logs (never propagates) their failures, also when there is only one.
   */
  static OutboxRelayListener composite(List<OutboxRelayListener> listeners) {
    if (listeners.isEmpty()) {
      return noop();
    }
    return new CompositeOutboxRelayListener(listeners);
  }

  /** {@code count} rows were published and committed as published. */
  default void published(int count) {}

  /** The target bus failed to publish a row; it stays pending and is retried. */
  default void publishFailed() {}

  /** {@code count} unreadable rows were set aside. */
  default void setAside(int count) {}
}
