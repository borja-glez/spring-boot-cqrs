package com.borjaglez.cqrs.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

import com.borjaglez.cqrs.retry.BackoffStrategy;
import com.borjaglez.cqrs.retry.RetryMiddleware;
import com.borjaglez.cqrs.retry.RetryPolicy;

/**
 * Registers the {@link RetryMiddleware} for commands and queries. Opt-in: only when {@code
 * cqrs.retry.enabled=true} and the application defines no {@link RetryMiddleware} of its own.
 */
@AutoConfiguration
@AutoConfigureAfter(CqrsAutoConfiguration.class)
@ConditionalOnProperty(prefix = "cqrs.retry", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CqrsProperties.class)
public class CqrsRetryAutoConfiguration {

  private static final String PREFIX = "cqrs.retry.";

  @Bean
  @ConditionalOnMissingBean
  public RetryMiddleware retryMiddleware(CqrsProperties properties, ResourceLoader resourceLoader) {
    return RetryMiddleware.builder()
        .defaultPolicy(retryPolicy(properties.getRetry(), resourceLoader.getClassLoader()))
        .build();
  }

  static RetryPolicy retryPolicy(CqrsProperties.RetryProperties retry, ClassLoader classLoader) {
    if (retry.getMaxAttempts() < 1) {
      throw invalid("max-attempts", retry.getMaxAttempts(), "must be at least 1");
    }
    RetryPolicy.Builder builder =
        RetryPolicy.builder()
            .maxAttempts(retry.getMaxAttempts())
            .backoff(backoff(retry.getBackoff()))
            .noRetryOn(
                exceptionTypes(
                    "non-retriable-exceptions", retry.getNonRetriableExceptions(), classLoader));
    List<Class<? extends Throwable>> retriable =
        exceptionTypes("retriable-exceptions", retry.getRetriableExceptions(), classLoader);
    if (!retriable.isEmpty()) {
      builder.retryOn(retriable);
    }
    return builder.build();
  }

  private static BackoffStrategy backoff(CqrsProperties.BackoffProperties backoff) {
    Duration initialDelay = nonNegative("backoff.initial-delay", backoff.getInitialDelay());
    Duration maxDelay = nonNegative("backoff.max-delay", backoff.getMaxDelay());
    double multiplier = backoff.getMultiplier();
    if (!(multiplier >= 1)) {
      throw invalid("backoff.multiplier", multiplier, "must be at least 1");
    }
    double jitterFactor = backoff.getJitterFactor();
    if (!(jitterFactor >= 0 && jitterFactor <= 1)) {
      throw invalid("backoff.jitter-factor", jitterFactor, "must be between 0 and 1");
    }
    if (backoff.getStrategy() == null) {
      throw invalid("backoff.strategy", null, "must be fixed, exponential or exponential-jitter");
    }
    return switch (backoff.getStrategy()) {
      case FIXED -> BackoffStrategy.fixed(initialDelay);
      case EXPONENTIAL -> BackoffStrategy.exponential(initialDelay, multiplier, maxDelay);
      case EXPONENTIAL_JITTER ->
          BackoffStrategy.exponentialWithJitter(initialDelay, multiplier, maxDelay, jitterFactor);
    };
  }

  private static Duration nonNegative(String name, Duration value) {
    if (value == null || value.isNegative()) {
      throw invalid(name, value, "must not be negative");
    }
    return value;
  }

  private static List<Class<? extends Throwable>> exceptionTypes(
      String name, List<String> classNames, ClassLoader classLoader) {
    List<Class<? extends Throwable>> types = new ArrayList<>();
    for (String className : classNames) {
      Class<?> type;
      try {
        type = ClassUtils.forName(className, classLoader);
      } catch (ClassNotFoundException | LinkageError e) {
        throw invalid(name, className, "class not found");
      }
      if (!Throwable.class.isAssignableFrom(type)) {
        throw invalid(name, className, "is not a java.lang.Throwable");
      }
      types.add(type.asSubclass(Throwable.class));
    }
    return types;
  }

  private static InvalidConfigurationPropertyValueException invalid(
      String name, Object value, String reason) {
    return new InvalidConfigurationPropertyValueException(PREFIX + name, value, reason);
  }
}
