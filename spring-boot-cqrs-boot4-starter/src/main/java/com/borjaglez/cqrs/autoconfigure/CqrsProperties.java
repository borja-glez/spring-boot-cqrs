package com.borjaglez.cqrs.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.retry.BackoffStrategy;
import com.borjaglez.cqrs.retry.RetryPolicy;
import com.borjaglez.cqrs.tracing.TracingMiddleware;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "cqrs")
public class CqrsProperties {
  private NamingProperties naming = new NamingProperties();
  private EventsProperties events = new EventsProperties();
  private ValidationProperties validation = new ValidationProperties();
  private ObservabilityProperties observability = new ObservabilityProperties();
  private IntrospectionProperties introspection = new IntrospectionProperties();
  private ContextProperties context = new ContextProperties();
  private TracingProperties tracing = new TracingProperties();
  private RetryProperties retry = new RetryProperties();
  private IdempotencyProperties idempotency = new IdempotencyProperties();

  @Getter
  @Setter
  public static class NamingProperties {
    private String prefix = "";
  }

  @Getter
  @Setter
  public static class EventsProperties {
    private boolean transactional = true;
  }

  @Getter
  @Setter
  public static class ValidationProperties {
    private boolean enabled = true;
  }

  @Getter
  @Setter
  public static class ObservabilityProperties {
    private boolean enabled = true;
  }

  @Getter
  @Setter
  public static class IntrospectionProperties {
    private boolean logHandlersOnStartup = false;
  }

  @Getter
  @Setter
  public static class ContextProperties {
    private boolean enabled = true;
    private boolean autoCorrelationId = true;
    private List<String> mdcKeys = new ArrayList<>(List.of(MessageContext.CORRELATION_ID_KEY));
    private String headerPrefix = "cqrs.context.";
  }

  @Getter
  @Setter
  public static class TracingProperties {
    private boolean enabled = true;
    private String observationName = TracingMiddleware.DEFAULT_OBSERVATION_NAME;
  }

  @Getter
  @Setter
  public static class RetryProperties {
    /** Whether to register the in-process retry middleware for commands and queries. */
    private boolean enabled = false;

    /** Total attempts, the first one included; 1 disables retries. */
    private int maxAttempts = RetryPolicy.DEFAULT_MAX_ATTEMPTS;

    private BackoffProperties backoff = new BackoffProperties();

    /**
     * Fully-qualified exception class names that make a failure retriable. Empty means the default
     * set (java.lang.RuntimeException).
     */
    private List<String> retriableExceptions = new ArrayList<>();

    /** Fully-qualified exception class names added to the default non-retriable set. */
    private List<String> nonRetriableExceptions = new ArrayList<>();
  }

  @Getter
  @Setter
  public static class BackoffProperties {
    /** Backoff strategy between attempts. */
    private Strategy strategy = Strategy.EXPONENTIAL_JITTER;

    /** Delay after the first failed attempt; the constant delay of the fixed strategy. */
    private Duration initialDelay = BackoffStrategy.DEFAULT_INITIAL_DELAY;

    /** Factor applied to the delay after every failed attempt; at least 1. */
    private double multiplier = BackoffStrategy.DEFAULT_MULTIPLIER;

    /** Upper bound of the delay. */
    private Duration maxDelay = BackoffStrategy.DEFAULT_MAX_DELAY;

    /** Random spread of the delay, between 0 and 1 (0.1 means +/-10 %). */
    private double jitterFactor = BackoffStrategy.DEFAULT_JITTER_FACTOR;

    public enum Strategy {
      FIXED,
      EXPONENTIAL,
      EXPONENTIAL_JITTER
    }
  }

  @Getter
  @Setter
  public static class IdempotencyProperties {
    /**
     * Store of @Idempotent handlers: jdbc (the default when spring-boot-cqrs-jdbc and a DataSource
     * are present) or in-memory (single instance, tests).
     */
    private StoreType store;

    /** How long a processed message is remembered. */
    private Duration retention = Duration.ofDays(7);

    private InMemoryIdempotencyProperties inMemory = new InMemoryIdempotencyProperties();

    public enum StoreType {
      JDBC,
      IN_MEMORY
    }
  }

  @Getter
  @Setter
  public static class InMemoryIdempotencyProperties {
    /** How long a delivery being processed blocks duplicates in the in-memory store. */
    private Duration lease = Duration.ofMinutes(5);
  }
}
