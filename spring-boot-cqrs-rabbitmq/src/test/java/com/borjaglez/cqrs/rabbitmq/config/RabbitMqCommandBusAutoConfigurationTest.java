package com.borjaglez.cqrs.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;

class RabbitMqCommandBusAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  RabbitAutoConfiguration.class,
                  RabbitMqCqrsAutoConfiguration.class,
                  RabbitMqCommandBusAutoConfiguration.class))
          .withBean(CommandHandlerRegistry.class, CommandHandlerRegistry::new)
          .withBean(MessageNamingStrategy.class, () -> new DefaultMessageNamingStrategy("cqrs"));

  @Test
  void shouldCreateCommandBusAndDeclarables() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RabbitMqCommandBus.class);
          assertThat(context).hasBean("cqrsCommandDeclarables");
        });
  }

  @Test
  void shouldNotCreateBeansWhenDisabled() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqCommandBus.class);
            });
  }

  @Test
  void shouldNotCreateBeansWithoutCommandHandlerRegistry() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                RabbitAutoConfiguration.class,
                RabbitMqCqrsAutoConfiguration.class,
                RabbitMqCommandBusAutoConfiguration.class))
        .withBean(MessageNamingStrategy.class, () -> new DefaultMessageNamingStrategy("cqrs"))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqCommandBus.class);
            });
  }
}
