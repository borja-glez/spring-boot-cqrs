package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.tracing.TracingMiddleware;

import io.micrometer.observation.ObservationRegistry;

class CqrsTracingAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class, CqrsTracingAutoConfiguration.class));

  @Test
  void tracingMiddlewareIsCreatedWhenObservationRegistryIsPresent() {
    contextRunner
        .withUserConfiguration(ObservationRegistryConfiguration.class)
        .run(context -> assertThat(context).hasSingleBean(TracingMiddleware.class));
  }

  @Test
  void tracingMiddlewareIsNotCreatedWhenObservationRegistryClassIsMissing() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(ObservationRegistry.class))
        .run(context -> assertThat(context).doesNotHaveBean(TracingMiddleware.class));
  }

  @Test
  void tracingMiddlewareIsNotCreatedWhenNoObservationRegistryBeanExists() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(TracingMiddleware.class));
  }

  @Test
  void tracingMiddlewareIsNotCreatedWhenDisabledByProperty() {
    contextRunner
        .withUserConfiguration(ObservationRegistryConfiguration.class)
        .withPropertyValues("cqrs.tracing.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(TracingMiddleware.class));
  }

  @Test
  void tracingMiddlewareIsCreatedWhenEnabledExplicitly() {
    contextRunner
        .withUserConfiguration(ObservationRegistryConfiguration.class)
        .withPropertyValues("cqrs.tracing.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(TracingMiddleware.class));
  }

  @Test
  void customObservationNamePropertyIsApplied() {
    contextRunner
        .withUserConfiguration(ObservationRegistryConfiguration.class)
        .withPropertyValues("cqrs.tracing.observation-name=my.app.dispatch")
        .run(
            context -> {
              CqrsProperties properties = context.getBean(CqrsProperties.class);
              assertThat(properties.getTracing().getObservationName()).isEqualTo("my.app.dispatch");
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class ObservationRegistryConfiguration {
    @Bean
    ObservationRegistry observationRegistry() {
      return ObservationRegistry.create();
    }
  }
}
