package com.borjaglez.cqrs.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;

@AutoConfiguration
@AutoConfigureAfter(CqrsAutoConfiguration.class)
@ConditionalOnClass(name = "org.slf4j.MDC")
@ConditionalOnProperty(
    prefix = "cqrs.context",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(CqrsProperties.class)
public class CqrsContextAutoConfiguration {

  @Bean
  public ContextPropagationMiddleware contextPropagationMiddleware(CqrsProperties properties) {
    CqrsProperties.ContextProperties context = properties.getContext();
    return new ContextPropagationMiddleware(context.isAutoCorrelationId(), context.getMdcKeys());
  }
}
