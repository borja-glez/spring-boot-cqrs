package com.borjaglez.cqrs.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;

class RabbitMqQueryBusAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  RabbitAutoConfiguration.class,
                  RabbitMqCqrsAutoConfiguration.class,
                  RabbitMqQueryBusAutoConfiguration.class))
          .withBean(QueryHandlerRegistry.class, QueryHandlerRegistry::new)
          .withBean(MessageNamingStrategy.class, () -> new DefaultMessageNamingStrategy("cqrs"));

  @Test
  void shouldCreateQueryBusAndDeclarables() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RabbitMqQueryBus.class);
          assertThat(context).hasBean("cqrsQueryDeclarables");
        });
  }

  @Test
  void shouldNotCreateBeansWhenDisabled() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqQueryBus.class);
            });
  }

  @Test
  void shouldNotCreateBeansWithoutQueryHandlerRegistry() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                RabbitAutoConfiguration.class,
                RabbitMqCqrsAutoConfiguration.class,
                RabbitMqQueryBusAutoConfiguration.class))
        .withBean(MessageNamingStrategy.class, () -> new DefaultMessageNamingStrategy("cqrs"))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqQueryBus.class);
            });
  }
}
