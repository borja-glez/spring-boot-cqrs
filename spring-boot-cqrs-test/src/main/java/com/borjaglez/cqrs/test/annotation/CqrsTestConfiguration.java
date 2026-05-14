package com.borjaglez.cqrs.test.annotation;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.command.spring.SpringCommandBus;
import com.borjaglez.cqrs.discovery.BeanPostProcessorHandlerDiscoverer;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.event.spring.SpringEventBus;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.query.spring.SpringQueryBus;
import com.borjaglez.cqrs.test.bus.SpyCommandBus;
import com.borjaglez.cqrs.test.bus.SpyEventBus;
import com.borjaglez.cqrs.test.bus.SpyQueryBus;

@Configuration(proxyBeanMethods = false)
public class CqrsTestConfiguration {

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public MessageNamingStrategy messageNamingStrategy() {
    return new DefaultMessageNamingStrategy("");
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public CommandHandlerRegistry commandHandlerRegistry() {
    return new CommandHandlerRegistry();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public EventHandlerRegistry eventHandlerRegistry() {
    return new EventHandlerRegistry();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public QueryHandlerRegistry queryHandlerRegistry() {
    return new QueryHandlerRegistry();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public BeanPostProcessorHandlerDiscoverer beanPostProcessorHandlerDiscoverer(
      CommandHandlerRegistry commandHandlerRegistry,
      EventHandlerRegistry eventHandlerRegistry,
      QueryHandlerRegistry queryHandlerRegistry,
      MessageNamingStrategy namingStrategy) {
    return new BeanPostProcessorHandlerDiscoverer(
        commandHandlerRegistry, eventHandlerRegistry, queryHandlerRegistry, namingStrategy);
  }

  @Bean
  @Primary
  public SpyCommandBus commandBus(
      CommandHandlerRegistry registry, ObjectProvider<List<BusMiddleware>> middlewares) {
    return new SpyCommandBus(
        new SpringCommandBus(registry, middlewares.getIfAvailable(Collections::emptyList)));
  }

  @Bean
  @Primary
  public SpyEventBus eventBus(
      EventHandlerRegistry registry, ObjectProvider<List<BusMiddleware>> middlewares) {
    return new SpyEventBus(
        new SpringEventBus(registry, middlewares.getIfAvailable(Collections::emptyList)));
  }

  @Bean
  @Primary
  public SpyQueryBus queryBus(
      QueryHandlerRegistry registry, ObjectProvider<List<BusMiddleware>> middlewares) {
    return new SpyQueryBus(
        new SpringQueryBus(registry, middlewares.getIfAvailable(Collections::emptyList)));
  }
}
