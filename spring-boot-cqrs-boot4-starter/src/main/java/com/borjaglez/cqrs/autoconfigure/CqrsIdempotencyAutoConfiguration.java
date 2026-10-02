package com.borjaglez.cqrs.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.idempotency.IdempotencyRegistrar;
import com.borjaglez.cqrs.idempotency.IdempotencyStore;
import com.borjaglez.cqrs.idempotency.IdempotentInvoker;
import com.borjaglez.cqrs.idempotency.InMemoryIdempotencyStore;

/**
 * Deduplicates {@code @Idempotent} handlers. The in-memory store is registered only with {@code
 * cqrs.idempotency.store=in-memory}; {@code spring-boot-cqrs-jdbc} contributes the JDBC store and
 * runs before this configuration.
 */
@AutoConfiguration(after = CqrsAutoConfiguration.class)
@EnableConfigurationProperties(CqrsProperties.class)
public class CqrsIdempotencyAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(IdempotencyStore.class)
  @ConditionalOnProperty(prefix = "cqrs.idempotency", name = "store", havingValue = "in-memory")
  public InMemoryIdempotencyStore inMemoryIdempotencyStore(CqrsProperties properties) {
    CqrsProperties.IdempotencyProperties idempotency = properties.getIdempotency();
    return new InMemoryIdempotencyStore(
        idempotency.getRetention(), idempotency.getInMemory().getLease());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(IdempotencyStore.class)
  public IdempotentInvoker idempotentInvoker(IdempotencyStore store) {
    return new IdempotentInvoker(store);
  }

  /**
   * Resolves the invoker only after startup, so the store's {@code DataSource} is not created
   * early.
   */
  @Bean
  @ConditionalOnMissingBean
  public IdempotencyRegistrar idempotencyRegistrar(
      CommandHandlerRegistry commandHandlerRegistry,
      EventHandlerRegistry eventHandlerRegistry,
      ObjectProvider<IdempotentInvoker> invoker) {
    return new IdempotencyRegistrar(
        commandHandlerRegistry, eventHandlerRegistry, invoker::getIfAvailable);
  }
}
