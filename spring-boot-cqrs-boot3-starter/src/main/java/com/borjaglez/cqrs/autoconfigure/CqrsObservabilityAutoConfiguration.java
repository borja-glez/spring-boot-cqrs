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
@AutoConfigureAfter(CqrsAutoConfiguration.class)
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
