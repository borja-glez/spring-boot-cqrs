package com.borjaglez.cqrs.autoconfigure;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;

import com.borjaglez.cqrs.aot.CqrsRuntimeHintsRegistrar;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.command.spring.SpringCommandBus;
import com.borjaglez.cqrs.discovery.BeanPostProcessorHandlerDiscoverer;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.event.spring.SpringEventBus;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.query.spring.SpringQueryBus;

@AutoConfiguration
@EnableConfigurationProperties(CqrsProperties.class)
@ImportRuntimeHints(CqrsRuntimeHintsRegistrar.class)
public class CqrsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public static MessageNamingStrategy messageNamingStrategy(Environment environment) {
    String prefix = environment.getProperty("cqrs.naming.prefix", "");
    return new DefaultMessageNamingStrategy(prefix);
  }

  @Bean
  @ConditionalOnMissingBean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public static CommandHandlerRegistry commandHandlerRegistry() {
    return new CommandHandlerRegistry();
  }

  @Bean
  @ConditionalOnMissingBean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public static EventHandlerRegistry eventHandlerRegistry() {
    return new EventHandlerRegistry();
  }

  @Bean
  @ConditionalOnMissingBean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public static QueryHandlerRegistry queryHandlerRegistry() {
    return new QueryHandlerRegistry();
  }

  @Bean
  @ConditionalOnMissingBean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public static BeanPostProcessorHandlerDiscoverer beanPostProcessorHandlerDiscoverer(
      CommandHandlerRegistry commandHandlerRegistry,
      EventHandlerRegistry eventHandlerRegistry,
      QueryHandlerRegistry queryHandlerRegistry,
      MessageNamingStrategy namingStrategy) {
    return new BeanPostProcessorHandlerDiscoverer(
        commandHandlerRegistry, eventHandlerRegistry, queryHandlerRegistry, namingStrategy);
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean(CommandBus.class)
  public SpringCommandBus springCommandBus(
      CommandHandlerRegistry commandHandlerRegistry,
      ObjectProvider<List<BusMiddleware>> middlewaresProvider) {
    return new SpringCommandBus(
        commandHandlerRegistry, middlewaresProvider.getIfAvailable(Collections::emptyList));
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean(EventBus.class)
  public SpringEventBus springEventBus(
      EventHandlerRegistry eventHandlerRegistry,
      ObjectProvider<List<BusMiddleware>> middlewaresProvider) {
    return new SpringEventBus(
        eventHandlerRegistry, middlewaresProvider.getIfAvailable(Collections::emptyList));
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean(QueryBus.class)
  public SpringQueryBus springQueryBus(
      QueryHandlerRegistry queryHandlerRegistry,
      ObjectProvider<List<BusMiddleware>> middlewaresProvider) {
    return new SpringQueryBus(
        queryHandlerRegistry, middlewaresProvider.getIfAvailable(Collections::emptyList));
  }
}
