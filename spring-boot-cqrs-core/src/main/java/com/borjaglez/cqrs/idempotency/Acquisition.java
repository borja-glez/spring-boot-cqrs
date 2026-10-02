package com.borjaglez.cqrs.idempotency;

/** Result of {@link IdempotencyStore#tryAcquire(String, String)}. */
public enum Acquisition {
  /** The caller owns the message for this handler and must run the handler. */
  ACQUIRED,
  /** The handler already processed the message, or another delivery is processing it. */
  DUPLICATE
}
