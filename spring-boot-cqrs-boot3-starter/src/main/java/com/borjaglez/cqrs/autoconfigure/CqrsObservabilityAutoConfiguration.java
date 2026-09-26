package com.borjaglez.cqrs.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.observability.MicrometerBusObservability;

import io.micrometer.core.instrument.MeterRegistry;

@AutoConfiguration
// The registry comes from Spring Boot's own auto-configuration: without running after it, the
// @ConditionalOnBean below never matches in an application (finding C29).
@AutoConfigureAfter(
    value = CqrsAutoConfiguration.class,
    name = {
      "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
      "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
      "org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration"
    })
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
@ConditionalOnProperty(
    prefix = "cqrs.observability",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CqrsObservabilityAutoConfiguration {

  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  public MicrometerBusObservability micrometerBusObservability(MeterRegistry meterRegistry) {
    return new MicrometerBusObservability(meterRegistry);
  }
}
