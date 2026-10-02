package com.borjaglez.cqrs.idempotency;

import java.util.function.Supplier;

/**
 * Remembers which handler processed which message. {@link IdempotentInvoker} calls {@link
 * #tryAcquire}, runs the handler, then calls {@link #complete} or, when the handler failed, {@link
 * #release}; all of it inside {@link #runInScope}.
 */
public interface IdempotencyStore {

  Acquisition tryAcquire(String handlerId, String messageId);

  void complete(String handlerId, String messageId);

  void release(String handlerId, String messageId);

  /**
   * Runs {@code work}, which acquires, runs the handler and completes or releases. A transactional
   * store overrides it to run everything in one transaction, so the marker commits with the
   * handler's effect.
   */
  default <T> T runInScope(Supplier<T> work) {
    return work.get();
  }
}
