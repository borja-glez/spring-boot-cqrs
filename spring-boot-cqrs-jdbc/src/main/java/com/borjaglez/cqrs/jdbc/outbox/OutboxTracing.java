package com.borjaglez.cqrs.jdbc.outbox;

import java.util.Map;

/**
 * Carries the trace of the publishing transaction to the relay: {@link #capture()} when the event
 * is stored, {@link #run} when it is relayed, so the broker send continues the original trace.
 */
public interface OutboxTracing {

  /** Headers that identify the current trace; empty when there is none. */
  Map<String, String> capture();

  /** Runs {@code action} in a span that continues the trace of {@code headers}. */
  void run(Map<String, String> headers, Runnable action);

  /** Tracing that captures nothing and runs actions as they are. */
  static OutboxTracing noop() {
    return NoopOutboxTracing.INSTANCE;
  }
}
