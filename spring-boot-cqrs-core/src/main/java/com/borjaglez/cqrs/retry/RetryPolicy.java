package com.borjaglez.cqrs.retry;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolationException;

import com.borjaglez.cqrs.command.CommandNotRegisteredException;
import com.borjaglez.cqrs.query.QueryNotRegisteredException;

/**
 * Decides whether a failed attempt is retried and how long to wait before the next one.
 *
 * <p>An exception is retriable when it, or a cause in its chain, is an instance of one of {@link
 * #retriableExceptions()} and neither it nor any cause is an instance of one of {@link
 * #nonRetriableExceptions()}: non-retriable wins. {@link #maxAttempts()} counts the first attempt,
 * so {@code 1} means no retry.
 *
 * <p>The policy does not depend on the bus, so it can drive any retry loop, not only {@link
 * RetryMiddleware}.
 *
 * @param maxAttempts total attempts, including the first one; at least {@code 1}
 * @param backoff delay between attempts
 * @param retriableExceptions exception types that make a failure retriable
 * @param nonRetriableExceptions exception types that make a failure final, even when a retriable
 *     type also matches
 */
public record RetryPolicy(
    int maxAttempts,
    BackoffStrategy backoff,
    Set<Class<? extends Throwable>> retriableExceptions,
    Set<Class<? extends Throwable>> nonRetriableExceptions) {

  /** Default number of attempts, the first one included. */
  public static final int DEFAULT_MAX_ATTEMPTS = 3;

  /** Exception types retried by default: every {@link RuntimeException}. */
  public static final Set<Class<? extends Throwable>> DEFAULT_RETRIABLE_EXCEPTIONS =
      Set.of(RuntimeException.class);

  /**
   * Exception types never retried by default: failures that another attempt cannot fix (invalid
   * arguments, validation errors, missing handlers).
   */
  public static final Set<Class<? extends Throwable>> DEFAULT_NON_RETRIABLE_EXCEPTIONS =
      Set.of(
          IllegalArgumentException.class,
          ConstraintViolationException.class,
          CommandNotRegisteredException.class,
          QueryNotRegisteredException.class);

  public RetryPolicy {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1, was " + maxAttempts);
    }
    if (backoff == null) {
      throw new IllegalArgumentException("backoff must not be null");
    }
    if (retriableExceptions == null) {
      throw new IllegalArgumentException("retriableExceptions must not be null");
    }
    if (nonRetriableExceptions == null) {
      throw new IllegalArgumentException("nonRetriableExceptions must not be null");
    }
    retriableExceptions = Set.copyOf(retriableExceptions);
    nonRetriableExceptions = Set.copyOf(nonRetriableExceptions);
  }

  /**
   * {@value #DEFAULT_MAX_ATTEMPTS} attempts, {@link BackoffStrategy#defaults()}, the default
   * retriable and non-retriable exceptions.
   */
  public static RetryPolicy defaults() {
    return builder().build();
  }

  /** A single attempt: failures are never retried. */
  public static RetryPolicy noRetry() {
    return builder().maxAttempts(1).build();
  }

  /** A builder initialised with the {@link #defaults()}. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns whether {@code failure} is retriable, looking at the exception and its causes.
   *
   * @param failure the exception thrown by the attempt; {@code null} is not retriable
   */
  public boolean isRetriable(Throwable failure) {
    boolean retriable = false;
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable current = failure;
        current != null && seen.add(current);
        current = current.getCause()) {
      if (isInstanceOfAny(nonRetriableExceptions, current)) {
        return false;
      }
      retriable |= isInstanceOfAny(retriableExceptions, current);
    }
    return retriable;
  }

  /**
   * Returns whether another attempt should follow {@code failedAttempts} failed attempts, the last
   * one having thrown {@code failure}.
   */
  public boolean shouldRetry(Throwable failure, int failedAttempts) {
    return failedAttempts < maxAttempts && isRetriable(failure);
  }

  private static boolean isInstanceOfAny(Set<Class<? extends Throwable>> types, Throwable failure) {
    return types.stream().anyMatch(type -> type.isInstance(failure));
  }

  /** Builder of {@link RetryPolicy}; starts from the {@link RetryPolicy#defaults()}. */
  public static final class Builder {

    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    private BackoffStrategy backoff = BackoffStrategy.defaults();
    private final Set<Class<? extends Throwable>> retriableExceptions =
        new LinkedHashSet<>(DEFAULT_RETRIABLE_EXCEPTIONS);
    private final Set<Class<? extends Throwable>> nonRetriableExceptions =
        new LinkedHashSet<>(DEFAULT_NON_RETRIABLE_EXCEPTIONS);

    private Builder() {}

    /** Total attempts, the first one included; {@code 1} disables retries. */
    public Builder maxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
      return this;
    }

    public Builder backoff(BackoffStrategy backoff) {
      this.backoff = backoff;
      return this;
    }

    /** Replaces the retriable exception types (by default, {@link RuntimeException}). */
    @SafeVarargs
    public final Builder retryOn(Class<? extends Throwable>... types) {
      return retryOn(List.of(types));
    }

    /** Replaces the retriable exception types (by default, {@link RuntimeException}). */
    public Builder retryOn(Collection<? extends Class<? extends Throwable>> types) {
      retriableExceptions.clear();
      retriableExceptions.addAll(types);
      return this;
    }

    /** Adds exception types to the non-retriable ones; the defaults are kept. */
    @SafeVarargs
    public final Builder noRetryOn(Class<? extends Throwable>... types) {
      return noRetryOn(List.of(types));
    }

    /** Adds exception types to the non-retriable ones; the defaults are kept. */
    public Builder noRetryOn(Collection<? extends Class<? extends Throwable>> types) {
      nonRetriableExceptions.addAll(types);
      return this;
    }

    public RetryPolicy build() {
      return new RetryPolicy(maxAttempts, backoff, retriableExceptions, nonRetriableExceptions);
    }
  }
}
