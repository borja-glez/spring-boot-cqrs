package com.borjaglez.cqrs.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.borjaglez.cqrs.rabbitmq.infrastructure.DefaultRabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

class RabbitMqCqrsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  RabbitAutoConfiguration.class, RabbitMqCqrsAutoConfiguration.class));

  @Test
  void shouldCreateDefaultBeans() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RabbitMqNamingStrategy.class);
          assertThat(context).hasSingleBean(RabbitMqPublisher.class);
          assertThat(context).hasSingleBean(RabbitMqBusDeclarationBuilder.class);
          assertThat(context).hasBean("cqrsMessageConverter");

          RabbitMqNamingStrategy namingStrategy = context.getBean(RabbitMqNamingStrategy.class);
          MessageConverter converter =
              context.getBean("cqrsMessageConverter", MessageConverter.class);
          assertThat(namingStrategy).isInstanceOf(DefaultRabbitMqNamingStrategy.class);
          assertThat(converter).isNotNull();
        });
  }

  @Test
  void shouldRespectCustomPrefix() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.prefix=custom")
        .run(
            context -> {
              RabbitMqNamingStrategy namingStrategy = context.getBean(RabbitMqNamingStrategy.class);
              assertThat(namingStrategy.exchange("commands")).isEqualTo("custom.commands");
            });
  }

  @Test
  void shouldNotCreateBeansWhenDisabled() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqNamingStrategy.class);
              assertThat(context).doesNotHaveBean(RabbitMqPublisher.class);
              assertThat(context).doesNotHaveBean(RabbitMqBusDeclarationBuilder.class);
            });
  }

  @Test
  void shouldBackOffWhenCustomNamingStrategyProvided() {
    contextRunner
        .withBean(
            RabbitMqNamingStrategy.class, () -> new DefaultRabbitMqNamingStrategy("my-custom"))
        .run(
            context -> {
              RabbitMqNamingStrategy namingStrategy = context.getBean(RabbitMqNamingStrategy.class);
              assertThat(namingStrategy.exchange("commands")).isEqualTo("my-custom.commands");
            });
  }

  @Test
  void shouldBindProperties() {
    contextRunner
        .withPropertyValues(
            "cqrs.rabbitmq.prefix=amj",
            "cqrs.rabbitmq.retry.max-attempts=5",
            "cqrs.rabbitmq.retry.ttl=2000",
            "cqrs.rabbitmq.commands.exchange=my-commands",
            "cqrs.rabbitmq.commands.concurrent-consumers=5",
            "cqrs.rabbitmq.commands.max-concurrent-consumers=10",
            "cqrs.rabbitmq.events.exchange=my-events",
            "cqrs.rabbitmq.queries.exchange=my-queries")
        .run(
            context -> {
              RabbitMqCqrsProperties props = context.getBean(RabbitMqCqrsProperties.class);
              assertThat(props.getPrefix()).isEqualTo("amj");
              assertThat(props.getRetry().getMaxAttempts()).isEqualTo(5);
              assertThat(props.getRetry().getTtl()).isEqualTo(2000);
              assertThat(props.getCommands().getExchange()).isEqualTo("my-commands");
              assertThat(props.getCommands().getConcurrentConsumers()).isEqualTo(5);
              assertThat(props.getCommands().getMaxConcurrentConsumers()).isEqualTo(10);
              assertThat(props.getEvents().getExchange()).isEqualTo("my-events");
              assertThat(props.getQueries().getExchange()).isEqualTo("my-queries");
            });
  }
}
