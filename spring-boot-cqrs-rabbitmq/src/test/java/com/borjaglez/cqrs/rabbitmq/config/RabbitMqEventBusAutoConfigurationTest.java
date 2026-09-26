package com.borjaglez.cqrs.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.event.spring.SpringEventBus;
import com.borjaglez.cqrs.naming.DefaultMessageNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus;

class RabbitMqEventBusAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  RabbitAutoConfiguration.class,
                  RabbitMqCqrsAutoConfiguration.class,
                  RabbitMqEventBusAutoConfiguration.class))
          .withBean(EventHandlerRegistry.class, EventHandlerRegistry::new)
          .withBean(MessageNamingStrategy.class, () -> new DefaultMessageNamingStrategy("cqrs"))
          .withBean(
              "springEventBus",
              EventBus.class,
              () -> {
                EventHandlerRegistry registry = new EventHandlerRegistry();
                return new SpringEventBus(registry, Collections.emptyList());
              });

  @Test
  void shouldCreateEventBusAndDeclarables() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RabbitMqEventBus.class);
          assertThat(context).hasBean("cqrsEventDeclarables");
        });
  }

  @Test
  void shouldNotCreateBeansWhenDisabled() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqEventBus.class);
            });
  }

  @Test
  void shouldNotCreateBeansWithoutEventHandlerRegistry() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                RabbitAutoConfiguration.class,
                RabbitMqCqrsAutoConfiguration.class,
                RabbitMqEventBusAutoConfiguration.class))
        .withBean(MessageNamingStrategy.class, () -> new DefaultMessageNamingStrategy("cqrs"))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqEventBus.class);
            });
  }

  @Test
  void shouldObserveTheListenerContainerLikeBootDoes() {
    contextRunner.run(
        context ->
            assertThat(
                    context.getBean(
                        "cqrsEventListenerContainer", SimpleMessageListenerContainer.class))
                .hasFieldOrPropertyWithValue("observationEnabled", false));
    contextRunner
        .withPropertyValues("spring.rabbitmq.listener.simple.observation-enabled=true")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "cqrsEventListenerContainer", SimpleMessageListenerContainer.class))
                    .hasFieldOrPropertyWithValue("observationEnabled", true));
  }

  @Test
  void shouldPassMaxAttemptsFromPropertiesToTheConsumer() {
    contextRunner.run(
        context ->
            assertThat(
                    context
                        .getBean("cqrsEventListenerContainer", SimpleMessageListenerContainer.class)
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
                                "cqrsEventListenerContainer", SimpleMessageListenerContainer.class)
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
              Declarables declarables = context.getBean("cqrsEventDeclarables", Declarables.class);
              assertThat(declarables.getDeclarablesByType(Binding.class))
                  .filteredOn(b -> b.getDestination().startsWith("cqrs.billing.events."))
                  .extracting(Binding::getRoutingKey)
                  .containsExactlyInAnyOrder("billing", "billing");
            });
  }
}
