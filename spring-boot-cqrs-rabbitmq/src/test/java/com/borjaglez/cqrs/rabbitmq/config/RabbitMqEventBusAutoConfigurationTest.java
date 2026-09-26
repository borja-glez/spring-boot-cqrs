package com.borjaglez.cqrs.rabbitmq.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

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

  @Test
  void shouldNotCreateEventBusBeansWhenEventsAreDisabled() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.events.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqEventBus.class);
              assertThat(context).doesNotHaveBean("cqrsEventDeclarables");
              assertThat(context).doesNotHaveBean("cqrsEventListenerContainer");
              assertThat(context).hasSingleBean(RabbitMqPublisher.class);
              assertThat(context).hasSingleBean(RabbitMqBusDeclarationBuilder.class);
            });
  }

  @Test
  void shouldCreateEventBusBeansWhenEventsAreExplicitlyEnabledAndOtherBusesAreDisabled() {
    contextRunner
        .withPropertyValues(
            "cqrs.rabbitmq.events.enabled=true",
            "cqrs.rabbitmq.commands.enabled=false",
            "cqrs.rabbitmq.queries.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RabbitMqEventBus.class);
              assertThat(context).hasBean("cqrsEventDeclarables");
              assertThat(context).hasBean("cqrsEventListenerContainer");
            });
  }

  @Test
  void shouldNotCreateEventBusBeansWhenRabbitMqIsDisabledEvenIfEventsAreEnabled() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.enabled=false", "cqrs.rabbitmq.events.enabled=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(RabbitMqEventBus.class);
              assertThat(context).doesNotHaveBean("cqrsEventDeclarables");
              assertThat(context).doesNotHaveBean("cqrsEventListenerContainer");
            });
  }

  @Test
  void rabbitMqEventBusShouldNotDependOnTheLocalEventBus() {
    contextRunner.run(
        context -> {
          assertThat(context).hasBean("springEventBus");
          assertThat(context.getBeanFactory().getDependenciesForBean("rabbitMqEventBus"))
              .isNotEmpty()
              .doesNotContain("springEventBus");
        });
  }

  @Test
  void shouldNotWaitForConfirmsByDefault() {
    contextRunner.run(
        context ->
            assertThat(context.getBean(RabbitMqEventBus.class))
                .extracting("confirmTimeout")
                .isNull());
  }

  @Test
  void shouldWaitForConfirmsWhenEnabled() {
    contextRunner
        .withPropertyValues(
            "spring.rabbitmq.publisher-confirm-type=correlated",
            "cqrs.rabbitmq.events.confirms.enabled=true",
            "cqrs.rabbitmq.events.confirms.timeout=2s")
        .run(
            context ->
                assertThat(context.getBean(RabbitMqEventBus.class))
                    .extracting("confirmTimeout")
                    .isEqualTo(Duration.ofSeconds(2)));
  }

  @Test
  void shouldFailToStartWhenConfirmsAreEnabledWithoutCorrelatedPublisherConfirms() {
    contextRunner
        .withPropertyValues("cqrs.rabbitmq.events.confirms.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("spring.rabbitmq.publisher-confirm-type=correlated"));
  }
}
