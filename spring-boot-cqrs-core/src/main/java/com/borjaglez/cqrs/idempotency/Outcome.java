package com.borjaglez.cqrs.idempotency;

/**
 * Result of {@link IdempotentInvoker#invoke}: whether the message was a duplicate and, when it was
 * not, what the handler returned (possibly {@code null}).
 */
public record Outcome<T>(boolean duplicate, T result) {

  public static <T> Outcome<T> skipped() {
    return new Outcome<>(true, null);
  }

  public static <T> Outcome<T> applied(T result) {
    return new Outcome<>(false, result);
  }
}
