package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.retry.BackoffStrategy;
import com.borjaglez.cqrs.retry.RetryMiddleware;
import com.borjaglez.cqrs.retry.RetryPolicy;

class CqrsRetryAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(CqrsAutoConfiguration.class, CqrsRetryAutoConfiguration.class));

  @Test
  void retryMiddlewareIsAbsentByDefault() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(RetryMiddleware.class));
  }

  @Test
  void retryMiddlewareIsAbsentWhenDisabled() {
    contextRunner
        .withPropertyValues("cqrs.retry.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(RetryMiddleware.class));
  }

  @Test
  void retryMiddlewareIsCreatedWithDefaultsWhenEnabled() {
    contextRunner
        .withPropertyValues("cqrs.retry.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RetryMiddleware.class);
              assertThat(context.getBean(RetryMiddleware.class).defaultPolicy())
                  .isEqualTo(RetryPolicy.defaults());
              assertThat(context.getBeansOfType(BusMiddleware.class).values())
                  .contains(context.getBean(RetryMiddleware.class));
            });
  }

  @Test
  void propertiesAreMappedToThePolicy() {
    contextRunner
        .withPropertyValues(
            "cqrs.retry.enabled=true",
            "cqrs.retry.max-attempts=5",
            "cqrs.retry.backoff.strategy=exponential",
            "cqrs.retry.backoff.initial-delay=50ms",
            "cqrs.retry.backoff.multiplier=3",
            "cqrs.retry.backoff.max-delay=2s",
            "cqrs.retry.retriable-exceptions=java.util.concurrent.TimeoutException,"
                + "java.lang.IllegalStateException",
            "cqrs.retry.non-retriable-exceptions=java.io.IOException")
        .run(
            context -> {
              RetryPolicy policy = context.getBean(RetryMiddleware.class).defaultPolicy();
              assertThat(policy.maxAttempts()).isEqualTo(5);
              assertThat(policy.backoff())
                  .isEqualTo(
                      BackoffStrategy.exponential(Duration.ofMillis(50), 3, Duration.ofSeconds(2)));
              assertThat(policy.retriableExceptions())
                  .containsExactlyInAnyOrder(TimeoutException.class, IllegalStateException.class);
              assertThat(policy.nonRetriableExceptions())
                  .contains(IOException.class, IllegalArgumentException.class);
            });
  }

  @Test
  void fixedAndJitterStrategiesAreMapped() {
    contextRunner
        .withPropertyValues(
            "cqrs.retry.enabled=true",
            "cqrs.retry.backoff.strategy=fixed",
            "cqrs.retry.backoff.initial-delay=250ms")
        .run(
            context ->
                assertThat(context.getBean(RetryMiddleware.class).defaultPolicy().backoff())
                    .isEqualTo(BackoffStrategy.fixed(Duration.ofMillis(250))));
    contextRunner
        .withPropertyValues(
            "cqrs.retry.enabled=true",
            "cqrs.retry.backoff.strategy=exponential-jitter",
            "cqrs.retry.backoff.jitter-factor=0.5")
        .run(
            context ->
                assertThat(context.getBean(RetryMiddleware.class).defaultPolicy().backoff())
                    .isEqualTo(
                        BackoffStrategy.exponentialWithJitter(
                            Duration.ofMillis(100), 2.0, Duration.ofSeconds(5), 0.5)));
  }

  @Test
  void userBeanWins() {
    contextRunner
        .withPropertyValues("cqrs.retry.enabled=true")
        .withUserConfiguration(UserRetryConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(RetryMiddleware.class);
              assertThat(context.getBean(RetryMiddleware.class))
                  .isSameAs(context.getBean("customRetry"));
            });
  }

  @Test
  void invalidPropertiesFailStartupNamingTheProperty() {
    assertStartupFails("cqrs.retry.max-attempts=0", "cqrs.retry.max-attempts");
    assertStartupFails("cqrs.retry.backoff.initial-delay=-1ms", "cqrs.retry.backoff.initial-delay");
    assertStartupFails("cqrs.retry.backoff.max-delay=-1s", "cqrs.retry.backoff.max-delay");
    assertStartupFails("cqrs.retry.backoff.multiplier=0.5", "cqrs.retry.backoff.multiplier");
    assertStartupFails("cqrs.retry.backoff.jitter-factor=-0.1", "cqrs.retry.backoff.jitter-factor");
    assertStartupFails("cqrs.retry.backoff.jitter-factor=1.5", "cqrs.retry.backoff.jitter-factor");
    assertStartupFails(
        "cqrs.retry.retriable-exceptions=com.example.Missing", "cqrs.retry.retriable-exceptions");
    assertStartupFails(
        "cqrs.retry.non-retriable-exceptions=java.lang.String",
        "cqrs.retry.non-retriable-exceptions");
  }

  @Test
  void nullValuesAreRejected() {
    ClassLoader classLoader = getClass().getClassLoader();
    CqrsProperties.RetryProperties nullStrategy = new CqrsProperties.RetryProperties();
    nullStrategy.getBackoff().setStrategy(null);
    CqrsProperties.RetryProperties nullInitialDelay = new CqrsProperties.RetryProperties();
    nullInitialDelay.getBackoff().setInitialDelay(null);
    CqrsProperties.RetryProperties nullMaxDelay = new CqrsProperties.RetryProperties();
    nullMaxDelay.getBackoff().setMaxDelay(null);

    assertThatThrownBy(() -> CqrsRetryAutoConfiguration.retryPolicy(nullStrategy, classLoader))
        .isInstanceOf(InvalidConfigurationPropertyValueException.class)
        .hasMessageContaining("cqrs.retry.backoff.strategy");
    assertThatThrownBy(() -> CqrsRetryAutoConfiguration.retryPolicy(nullInitialDelay, classLoader))
        .isInstanceOf(InvalidConfigurationPropertyValueException.class)
        .hasMessageContaining("cqrs.retry.backoff.initial-delay");
    assertThatThrownBy(() -> CqrsRetryAutoConfiguration.retryPolicy(nullMaxDelay, classLoader))
        .isInstanceOf(InvalidConfigurationPropertyValueException.class)
        .hasMessageContaining("cqrs.retry.backoff.max-delay");
  }

  @Test
  void emptyExceptionListsKeepTheDefaults() {
    RetryPolicy policy =
        CqrsRetryAutoConfiguration.retryPolicy(
            new CqrsProperties.RetryProperties(), getClass().getClassLoader());

    assertThat(policy).isEqualTo(RetryPolicy.defaults());
    assertThat(policy.retriableExceptions()).isEqualTo(RetryPolicy.DEFAULT_RETRIABLE_EXCEPTIONS);
    assertThat(policy.nonRetriableExceptions())
        .containsExactlyInAnyOrderElementsOf(RetryPolicy.DEFAULT_NON_RETRIABLE_EXCEPTIONS);
  }

  private void assertStartupFails(String property, String expectedName) {
    contextRunner
        .withPropertyValues("cqrs.retry.enabled=true", property)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .isInstanceOf(InvalidConfigurationPropertyValueException.class)
                  .hasMessageContaining(expectedName);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class UserRetryConfiguration {
    @Bean
    RetryMiddleware customRetry() {
      return RetryMiddleware.builder().defaultPolicy(RetryPolicy.noRetry()).build();
    }
  }
}
