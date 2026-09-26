package com.borjaglez.cqrs.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

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

  @Test
  void shouldObserveTheListenerContainerLikeBootDoes() {
    contextRunner.run(
        context ->
            assertThat(
                    context.getBean(
                        "cqrsCommandListenerContainer", SimpleMessageListenerContainer.class))
                .hasFieldOrPropertyWithValue("observationEnabled", false));
    contextRunner
        .withPropertyValues("spring.rabbitmq.listener.simple.observation-enabled=true")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "cqrsCommandListenerContainer", SimpleMessageListenerContainer.class))
                    .hasFieldOrPropertyWithValue("observationEnabled", true));
  }

  @Test
  void shouldPassMaxAttemptsFromPropertiesToTheConsumer() {
    contextRunner.run(
        context ->
            assertThat(
                    context
                        .getBean(
                            "cqrsCommandListenerContainer", SimpleMessageListenerContainer.class)
                        .getMessageListener())
                .extracting("delegate")
                .extracting("maxAttempts")
                .isEqualTo(3));
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.retry.max-attempts=1")
        .run(
            context ->
                assertThat(
                        context
                            .getBean(
                                "cqrsCommandListenerContainer",
                                SimpleMessageListenerContainer.class)
                            .getMessageListener())
                    .extracting("delegate")
                    .extracting("maxAttempts")
                    .isEqualTo(1));
  }

  @Test
  void shouldFailToStartWhenMaxAttemptsIsBelowOne() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.retry.max-attempts=0")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("max-attempts"));
  }

  @Test
  void shouldBindRetryAndDeadLetterQueuesWithTheApplicationName() {
    contextRunner
        .withPropertyValues("spring.application.name=billing")
        .run(
            context -> {
              Declarables declarables =
                  context.getBean("cqrsCommandDeclarables", Declarables.class);
              assertThat(declarables.getDeclarablesByType(Binding.class))
                  .filteredOn(b -> b.getDestination().startsWith("cqrs.billing.commands."))
                  .extracting(Binding::getRoutingKey)
                  .containsExactlyInAnyOrder("billing", "billing");
            });
  }

  @Test
  void shouldNotCreateCommandBusBeansWhenCommandsAreDisabled() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.commands.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqCommandBus.class);
              assertThat(context).doesNotHaveBean("cqrsCommandDeclarables");
              assertThat(context).doesNotHaveBean("cqrsCommandListenerContainer");
              assertThat(context).hasSingleBean(RabbitMqPublisher.class);
              assertThat(context).hasSingleBean(RabbitMqBusDeclarationBuilder.class);
            });
  }

  @Test
  void shouldCreateCommandBusBeansWhenCommandsAreExplicitlyEnabledAndOtherBusesAreDisabled() {
    contextRunner
        .withPropertyValues(
            "cqrs.rabbitmq.commands.enabled=true",
            "cqrs.rabbitmq.queries.enabled=false",
            "cqrs.rabbitmq.events.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RabbitMqCommandBus.class);
              assertThat(context).hasBean("cqrsCommandDeclarables");
              assertThat(context).hasBean("cqrsCommandListenerContainer");
            });
  }

  @Test
  void shouldNotCreateCommandBusBeansWhenRabbitMqIsDisabledEvenIfCommandsAreEnabled() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.enabled=false", "cqrs.rabbitmq.commands.enabled=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqCommandBus.class);
              assertThat(context).doesNotHaveBean("cqrsCommandDeclarables");
              assertThat(context).doesNotHaveBean("cqrsCommandListenerContainer");
            });
  }
}
