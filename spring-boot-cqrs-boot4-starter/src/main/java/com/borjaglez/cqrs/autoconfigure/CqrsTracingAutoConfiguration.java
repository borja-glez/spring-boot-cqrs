package com.borjaglez.cqrs.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.tracing.TracingMiddleware;

import io.micrometer.observation.ObservationRegistry;

@AutoConfiguration
@AutoConfigureAfter(CqrsAutoConfiguration.class)
@ConditionalOnClass(name = "io.micrometer.observation.ObservationRegistry")
@ConditionalOnProperty(
    prefix = "cqrs.tracing",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(CqrsProperties.class)
public class CqrsTracingAutoConfiguration {

  @Bean
  @ConditionalOnBean(ObservationRegistry.class)
  @ConditionalOnMissingBean
  public TracingMiddleware tracingMiddleware(
      ObservationRegistry registry, CqrsProperties properties) {
    return new TracingMiddleware(registry, properties.getTracing().getObservationName());
  }
}
