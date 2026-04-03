package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.observability.MicrometerBusObservability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CqrsObservabilityAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class, CqrsObservabilityAutoConfiguration.class));

  @Test
  void micrometerBusObservabilityIsCreatedWhenMeterRegistryIsPresent() {
    contextRunner
        .withUserConfiguration(MeterRegistryConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(MicrometerBusObservability.class);
            });
  }

  @Test
  void micrometerBusObservabilityIsNotCreatedWhenMeterRegistryClassIsMissing() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(MicrometerBusObservability.class);
            });
  }

  @Test
  void micrometerBusObservabilityIsNotCreatedWhenNoMeterRegistryBeanExists() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(MicrometerBusObservability.class);
        });
  }

  @Test
  void micrometerBusObservabilityIsNotCreatedWhenDisabledByProperty() {
    contextRunner
        .withUserConfiguration(MeterRegistryConfiguration.class)
        .withPropertyValues("cqrs.observability.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(MicrometerBusObservability.class);
            });
  }

  @Test
  void micrometerBusObservabilityIsCreatedWhenEnabledExplicitly() {
    contextRunner
        .withUserConfiguration(MeterRegistryConfiguration.class)
        .withPropertyValues("cqrs.observability.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(MicrometerBusObservability.class);
            });
  }

  @Test
  void observabilityEnabledByDefaultWhenPropertyNotSet() {
    contextRunner
        .withUserConfiguration(MeterRegistryConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(MicrometerBusObservability.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class MeterRegistryConfiguration {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
