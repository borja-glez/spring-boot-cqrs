package com.borjaglez.cqrs.retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.MiddlewareChain;
import com.borjaglez.cqrs.query.Query;

/**
 * Retries a failed command or query dispatch in process, waiting between attempts as the {@link
 * RetryPolicy} says.
 *
 * <p>Each attempt calls {@link MiddlewareChain#proceed} again, so every middleware ordered after
 * this one, and the handler, run once per attempt. When the policy gives up, the exception of the
 * last attempt is rethrown as is. Events and any other message pass through untouched: an event has
 * several handlers, and retrying the dispatch would run again the ones that already succeeded.
 *
 * <p>The policy is looked up by the exact class of the message ({@link Builder#override}), falling
 * back to the default policy.
 *
 * <p>Ordered at {@link #ORDER}: after context propagation and tracing, so one trace and correlation
 * id cover every attempt, and before validation, observability and unordered user middleware, so
 * each attempt is validated and timed on its own.
 */
@Order(RetryMiddleware.ORDER)
public class RetryMiddleware implements BusMiddleware {

  /** {@code Ordered.LOWEST_PRECEDENCE - 100}. */
  public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

  private final RetryPolicy defaultPolicy;
  private final Map<Class<?>, RetryPolicy> overrides;
  private final Sleeper sleeper;
  private final Supplier<RandomGenerator> random;

  private RetryMiddleware(Builder builder) {
    this.defaultPolicy = builder.defaultPolicy;
    this.overrides = Map.copyOf(builder.overrides);
    this.sleeper = builder.sleeper;
    this.random = builder.random;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** The policy applied to messages without an override. */
  public RetryPolicy defaultPolicy() {
    return defaultPolicy;
  }

  /** The policy applied to messages of exactly {@code messageType}. */
  public RetryPolicy policyFor(Class<?> messageType) {
    return overrides.getOrDefault(messageType, defaultPolicy);
  }

  @Override
  public Object process(Object message, MiddlewareChain chain) throws Exception {
    if (!(message instanceof Command || message instanceof Query)) {
      return chain.proceed(message);
    }
    RetryPolicy policy = policyFor(message.getClass());
    for (int attempt = 1; ; attempt++) {
      try {
        return chain.proceed(message);
      } catch (Exception failure) {
        if (!policy.shouldRetry(failure, attempt)) {
          throw failure;
        }
        waitBeforeRetry(policy.backoff().delayAfter(attempt, random.get()), failure);
      }
    }
  }

  private void waitBeforeRetry(Duration delay, Exception failure) throws Exception {
    try {
      sleeper.sleep(delay);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      failure.addSuppressed(interrupted);
      throw failure;
    }
  }

  /** Waits between attempts; replaced in tests so they never really sleep. */
  @FunctionalInterface
  interface Sleeper {
    void sleep(Duration delay) throws InterruptedException;
  }

  /** Builder of {@link RetryMiddleware}; the default policy is {@link RetryPolicy#defaults()}. */
  public static final class Builder {

    private RetryPolicy defaultPolicy = RetryPolicy.defaults();
    private final Map<Class<?>, RetryPolicy> overrides = new HashMap<>();
    private Sleeper sleeper = Thread::sleep;
    private Supplier<RandomGenerator> random = ThreadLocalRandom::current;

    private Builder() {}

    /** Policy for messages without an override. */
    public Builder defaultPolicy(RetryPolicy policy) {
      this.defaultPolicy = Objects.requireNonNull(policy, "policy");
      return this;
    }

    /**
     * Policy for messages of exactly {@code messageType} (subclasses are not matched); use {@link
     * RetryPolicy#noRetry()} to exclude a type.
     */
    public Builder override(Class<?> messageType, RetryPolicy policy) {
      overrides.put(messageType, policy);
      return this;
    }

    Builder sleeper(Sleeper sleeper) {
      this.sleeper = sleeper;
      return this;
    }

    Builder random(Supplier<RandomGenerator> random) {
      this.random = random;
      return this;
    }

    public RetryMiddleware build() {
      return new RetryMiddleware(this);
    }
  }
}
