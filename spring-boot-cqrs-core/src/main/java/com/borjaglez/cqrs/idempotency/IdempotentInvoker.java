package com.borjaglez.cqrs.idempotency;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Runs an effect at most once per handler and message. The registries use it for {@link Idempotent}
 * handlers; applications can use it directly, for example in a plain listener.
 */
public final class IdempotentInvoker {

  private final IdempotencyStore store;

  public IdempotentInvoker(IdempotencyStore store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  public <T> Outcome<T> invoke(String handlerId, String messageId, Supplier<T> effect) {
    return store.runInScope(
        () -> {
          if (store.tryAcquire(handlerId, messageId) == Acquisition.DUPLICATE) {
            return Outcome.skipped();
          }
          T result;
          try {
            result = effect.get();
          } catch (RuntimeException | Error e) {
            store.release(handlerId, messageId);
            throw e;
          }
          store.complete(handlerId, messageId);
          return Outcome.applied(result);
        });
  }
}
