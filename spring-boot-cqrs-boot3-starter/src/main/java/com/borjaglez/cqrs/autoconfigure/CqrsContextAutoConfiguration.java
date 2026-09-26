package com.borjaglez.cqrs.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContextThreadLocalAccessor;

import io.micrometer.context.ContextRegistry;

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

  /**
   * Registers {@link MessageContextThreadLocalAccessor} in the global Micrometer {@link
   * ContextRegistry} so executors decorated with {@code ContextPropagatingTaskDecorator} carry the
   * {@code MessageContext} across threads.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "io.micrometer.context.ContextRegistry")
  static class MessageContextThreadLocalAccessorConfiguration {

    @Bean
    public MessageContextThreadLocalAccessor messageContextThreadLocalAccessor() {
      MessageContextThreadLocalAccessor accessor = new MessageContextThreadLocalAccessor();
      ContextRegistry.getInstance().registerThreadLocalAccessor(accessor);
      return accessor;
    }
  }
}
