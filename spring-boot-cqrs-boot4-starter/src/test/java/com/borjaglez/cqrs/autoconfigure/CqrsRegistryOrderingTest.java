package com.borjaglez.cqrs.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.borjaglez.cqrs.observability.MicrometerBusObservability;
import com.borjaglez.cqrs.tracing.TracingMiddleware;

/**
 * In an application the registries come from Spring Boot's auto-configurations, not from user
 * configuration: the CQRS ones must run after them or their {@code @ConditionalOnBean} never
 * matches (finding C29). The CQRS auto-configurations are listed first on purpose.
 */
class CqrsRegistryOrderingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class,
                  CqrsObservabilityAutoConfiguration.class,
                  CqrsTracingAutoConfiguration.class,
                  ObservationAutoConfiguration.class,
                  MetricsAutoConfiguration.class,
                  CompositeMeterRegistryAutoConfiguration.class,
                  SimpleMetricsExportAutoConfiguration.class));

  @Test
  void busMetricsAndTracingFollowTheRegistriesOfSpringBoot() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(MicrometerBusObservability.class);
          assertThat(context).hasSingleBean(TracingMiddleware.class);
        });
  }
}
