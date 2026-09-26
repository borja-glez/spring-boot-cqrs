package com.borjaglez.cqrs.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DefaultErrorHandler;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.kafka.KafkaCommandBus;
import com.borjaglez.cqrs.kafka.KafkaEventBus;
import com.borjaglez.cqrs.kafka.KafkaQueryBus;
import com.borjaglez.cqrs.kafka.fixtures.OutboundBlocker;
import com.borjaglez.cqrs.kafka.fixtures.TestCommand;
import com.borjaglez.cqrs.kafka.fixtures.TestEvent;
import com.borjaglez.cqrs.kafka.fixtures.TestQuery;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaPartitionKeyStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus;
import com.borjaglez.cqrs.rabbitmq.config.RabbitMqCommandBusAutoConfiguration;
import com.borjaglez.cqrs.rabbitmq.config.RabbitMqCqrsAutoConfiguration;
import com.borjaglez.cqrs.rabbitmq.config.RabbitMqEventBusAutoConfiguration;
import com.borjaglez.cqrs.rabbitmq.config.RabbitMqQueryBusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;

class KafkaCqrsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "spring.application.name=orders-service",
              "spring.kafka.bootstrap-servers=localhost:9092",
              // No broker runs here: do not wait for one when declaring the topics.
              "spring.kafka.admin.operation-timeout=1s",
              "spring.kafka.admin.close-timeout=1s")
          .withBean(ObjectMapper.class, ObjectMapper::new)
          .withBean(CommandHandlerRegistry.class, CommandHandlerRegistry::new)
          .withBean(EventHandlerRegistry.class, EventHandlerRegistry::new)
          .withBean(QueryHandlerRegistry.class, QueryHandlerRegistry::new)
          .withConfiguration(
              AutoConfigurations.of(
                  CqrsAutoConfiguration.class,
                  CqrsSerializationAutoConfiguration.class,
                  KafkaAutoConfiguration.class,
                  KafkaCqrsAutoConfiguration.class,
                  KafkaCommandBusAutoConfiguration.class,
                  KafkaEventBusAutoConfiguration.class,
                  KafkaQueryBusAutoConfiguration.class));

  @Test
  void shouldCreateKafkaInfrastructureAndBuses() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(KafkaTopicNamingStrategy.class);
          assertThat(context).hasSingleBean(KafkaPartitionKeyStrategy.class);
          assertThat(context).hasSingleBean(KafkaRequestReplyClient.class);
          assertThat(context).hasSingleBean(KafkaCommandBus.class);
          assertThat(context).hasSingleBean(KafkaEventBus.class);
          assertThat(context).hasSingleBean(KafkaQueryBus.class);
          assertThat(context).hasBean("cqrsKafkaReplyContainer");
          assertThat(context).hasBean("cqrsCommandsTopic");
          assertThat(context).hasBean("cqrsEventsTopic");
          assertThat(context).hasBean("cqrsQueriesTopic");
          assertThat(context).hasBean("cqrsRepliesTopic");
        });
  }

  @Test
  void shouldRunSideBySideWithTheRabbitMqModule() {
    // Both modules declared listener containers with the same bean names, so an application
    // using Kafka for events and RabbitMQ for commands failed to start (finding C26).
    contextRunner
        .withPropertyValues("spring.rabbitmq.host=localhost", "spring.rabbitmq.port=1")
        .withConfiguration(
            AutoConfigurations.of(
                RabbitAutoConfiguration.class,
                RabbitMqCqrsAutoConfiguration.class,
                RabbitMqCommandBusAutoConfiguration.class,
                RabbitMqEventBusAutoConfiguration.class,
                RabbitMqQueryBusAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(KafkaEventBus.class);
              assertThat(context).hasSingleBean(RabbitMqEventBus.class);
              assertThat(context).hasBean("cqrsKafkaEventListenerContainer");
              assertThat(context).hasBean("cqrsKafkaCommandListenerContainer");
              assertThat(context).hasBean("cqrsKafkaQueryListenerContainer");
              assertThat(context).hasBean("cqrsEventListenerContainer");
            });
  }

  @Test
  void kafkaEventBusShouldNotDependOnTheLocalEventBus() {
    contextRunner.run(
        context -> {
          assertThat(context).hasBean("springEventBus");
          assertThat(context.getBeanFactory().getDependenciesForBean("kafkaEventBus"))
              .isNotEmpty()
              .doesNotContain("springEventBus");
        });
  }

  @Test
  void shouldRespectCustomPrefix() {
    contextRunner
        .withPropertyValues("cqrs.kafka.prefix=custom")
        .run(
            context -> {
              KafkaTopicNamingStrategy namingStrategy =
                  context.getBean(KafkaTopicNamingStrategy.class);
              assertThat(namingStrategy.topic("commands")).isEqualTo("custom.commands");
              assertThat(context.getBean(KafkaEventBus.class)).isNotNull();
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldBuildFactoriesFromTheSpringBootKafkaFactories() {
    contextRunner
        .withPropertyValues("spring.kafka.producer.acks=all", "spring.kafka.consumer.group-id=g1")
        .run(
            context -> {
              var producer =
                  (DefaultKafkaProducerFactory<String, byte[]>)
                      context.getBean("cqrsKafkaProducerFactory", ProducerFactory.class);
              assertThat(producer.getConfigurationProperties())
                  .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, List.of("localhost:9092"))
                  .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                  .containsEntry(
                      ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
              var consumer =
                  (DefaultKafkaConsumerFactory<String, byte[]>)
                      context.getBean("cqrsKafkaConsumerFactory", ConsumerFactory.class);
              assertThat(consumer.getConfigurationProperties())
                  .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "g1")
                  .containsEntry(
                      ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldWorkWithoutSpringBootKafkaAutoConfiguration() {
    // Spring Boot moved its Kafka auto-configuration between major versions (finding C6), so the
    // CQRS module must not depend on it; the bootstrap servers property is enough.
    new ApplicationContextRunner()
        .withPropertyValues(
            "spring.application.name=orders-service",
            "spring.kafka.bootstrap-servers=localhost:19092")
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(CommandHandlerRegistry.class, CommandHandlerRegistry::new)
        .withBean(EventHandlerRegistry.class, EventHandlerRegistry::new)
        .withBean(QueryHandlerRegistry.class, QueryHandlerRegistry::new)
        .withConfiguration(
            AutoConfigurations.of(
                CqrsAutoConfiguration.class,
                CqrsSerializationAutoConfiguration.class,
                KafkaCqrsAutoConfiguration.class,
                KafkaEventBusAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var producer =
                  (DefaultKafkaProducerFactory<String, byte[]>)
                      context.getBean("cqrsKafkaProducerFactory", ProducerFactory.class);
              assertThat(producer.getConfigurationProperties())
                  .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
              assertThat(context).hasSingleBean(KafkaEventBus.class);
            });
  }

  @Test
  void commandsCanBeLeftOffKafka() {
    contextRunner
        .withPropertyValues("cqrs.kafka.commands.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(KafkaCommandBus.class);
              assertThat(context).doesNotHaveBean("cqrsCommandsTopic");
              assertThat(context).hasSingleBean(KafkaQueryBus.class);
              assertThat(context).hasSingleBean(KafkaEventBus.class);
              assertThat(context).hasSingleBean(KafkaRequestReplyClient.class);
            });
  }

  @Test
  void eventsOnlyApplicationsDoNotStartRequestReplyInfrastructure() {
    contextRunner
        .withPropertyValues("cqrs.kafka.commands.enabled=false", "cqrs.kafka.queries.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(KafkaEventBus.class);
              assertThat(context).doesNotHaveBean(KafkaCommandBus.class);
              assertThat(context).doesNotHaveBean(KafkaQueryBus.class);
              assertThat(context).doesNotHaveBean(KafkaRequestReplyClient.class);
              assertThat(context).doesNotHaveBean("cqrsKafkaReplyContainer");
              assertThat(context).doesNotHaveBean("cqrsRepliesTopic");
            });
  }

  @Test
  void eventsCanBeLeftOffKafka() {
    contextRunner
        .withPropertyValues("cqrs.kafka.events.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(KafkaEventBus.class);
              assertThat(context).doesNotHaveBean("cqrsEventsTopic");
              assertThat(context).hasSingleBean(KafkaCommandBus.class);
            });
  }

  @Test
  void shouldNotCreateKafkaBeansWhenDisabled() {
    contextRunner
        .withPropertyValues("cqrs.kafka.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(KafkaTopicNamingStrategy.class);
              assertThat(context).doesNotHaveBean(KafkaRequestReplyClient.class);
              assertThat(context).doesNotHaveBean(KafkaCommandBus.class);
              assertThat(context).doesNotHaveBean(KafkaEventBus.class);
              assertThat(context).doesNotHaveBean(KafkaQueryBus.class);
            });
  }

  @Test
  void shouldObserveTheTemplateAndContainersLikeBootDoes() {
    String[] containers = {
      "cqrsKafkaCommandListenerContainer",
      "cqrsKafkaEventListenerContainer",
      "cqrsKafkaQueryListenerContainer",
      "cqrsKafkaReplyContainer"
    };
    contextRunner.run(
        context -> {
          assertThat(context.getBean("cqrsKafkaTemplate", KafkaTemplate.class))
              .hasFieldOrPropertyWithValue("observationEnabled", false);
          for (String name : containers) {
            assertThat(
                    context
                        .getBean(name, ConcurrentMessageListenerContainer.class)
                        .getContainerProperties()
                        .isObservationEnabled())
                .as(name)
                .isFalse();
          }
        });
    contextRunner
        .withPropertyValues(
            "spring.kafka.template.observation-enabled=true",
            "spring.kafka.listener.observation-enabled=true")
        .run(
            context -> {
              assertThat(context.getBean("cqrsKafkaTemplate", KafkaTemplate.class))
                  .hasFieldOrPropertyWithValue("observationEnabled", true);
              for (String name : containers) {
                assertThat(
                        context
                            .getBean(name, ConcurrentMessageListenerContainer.class)
                            .getContainerProperties()
                            .isObservationEnabled())
                    .as(name)
                    .isTrue();
              }
            });
  }

  private static final String[] BUS_CONTAINERS = {
    "cqrsKafkaCommandListenerContainer",
    "cqrsKafkaEventListenerContainer",
    "cqrsKafkaQueryListenerContainer"
  };

  @Test
  void shouldDeclareAPerApplicationDeadLetterTopicPerBus() {
    contextRunner.run(
        context -> {
          assertThat(context.getBean("cqrsCommandsDeadLetterTopic", NewTopic.class).name())
              .isEqualTo("cqrs.orders-service.commands.dlt");
          assertThat(context.getBean("cqrsEventsDeadLetterTopic", NewTopic.class).name())
              .isEqualTo("cqrs.orders-service.events.dlt");
          assertThat(context.getBean("cqrsQueriesDeadLetterTopic", NewTopic.class).name())
              .isEqualTo("cqrs.orders-service.queries.dlt");
        });
  }

  @Test
  void shouldNotDeclareDeadLetterTopicsWhenTopicsAreNotCreated() {
    contextRunner
        .withPropertyValues("cqrs.kafka.auto-create-topics=false")
        .run(context -> assertNoDeadLetterTopics(context));
  }

  @Test
  void shouldNotDeclareDeadLetterTopicsWhenDeadLetteringIsDisabled() {
    contextRunner
        .withPropertyValues("cqrs.kafka.error-handling.dead-letter.enabled=false")
        .run(
            context -> {
              assertNoDeadLetterTopics(context);
              assertThat(context).hasBean("cqrsEventsTopic");
            });
  }

  @Test
  void shouldApplyItsOwnErrorHandlerWithoutExposingItAsABean() {
    // A CommonErrorHandler bean would also be applied to the application's @KafkaListeners.
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(CommonErrorHandler.class);
          for (String name : BUS_CONTAINERS) {
            ConcurrentMessageListenerContainer<?, ?> container =
                context.getBean(name, ConcurrentMessageListenerContainer.class);
            assertThat(container.getCommonErrorHandler())
                .as(name)
                .isInstanceOf(DefaultErrorHandler.class);
            assertThat(container.getContainerProperties().isDeliveryAttemptHeader()).isTrue();
          }
          assertThat(
                  context
                      .getBean("cqrsKafkaReplyContainer", ConcurrentMessageListenerContainer.class)
                      .getCommonErrorHandler())
              .isNull();
        });
  }

  @Test
  void shouldApplyTheApplicationsErrorHandlerToTheBusContainers() {
    CommonErrorHandler errorHandler = mock(CommonErrorHandler.class);
    contextRunner
        .withBean("myErrorHandler", CommonErrorHandler.class, () -> errorHandler)
        .run(
            context -> {
              for (String name : BUS_CONTAINERS) {
                assertThat(
                        context
                            .getBean(name, ConcurrentMessageListenerContainer.class)
                            .getCommonErrorHandler())
                    .as(name)
                    .isSameAs(errorHandler);
              }
              assertThat(
                      context
                          .getBean(
                              "cqrsKafkaReplyContainer", ConcurrentMessageListenerContainer.class)
                          .getCommonErrorHandler())
                  .isNotSameAs(errorHandler);
            });
  }

  @Test
  void shouldPreferTheErrorHandlerNamedForTheCqrsContainers() {
    CommonErrorHandler cqrsErrorHandler = mock(CommonErrorHandler.class);
    contextRunner
        .withBean(
            "otherErrorHandler", CommonErrorHandler.class, () -> mock(CommonErrorHandler.class))
        .withBean("cqrsKafkaErrorHandler", CommonErrorHandler.class, () -> cqrsErrorHandler)
        .run(
            context -> {
              for (String name : BUS_CONTAINERS) {
                assertThat(
                        context
                            .getBean(name, ConcurrentMessageListenerContainer.class)
                            .getCommonErrorHandler())
                    .as(name)
                    .isSameAs(cqrsErrorHandler);
              }
            });
  }

  @Test
  void shouldBindTheErrorHandlingProperties() {
    contextRunner
        .withPropertyValues(
            "cqrs.kafka.error-handling.max-attempts=5",
            "cqrs.kafka.error-handling.back-off.initial-interval=250ms",
            "cqrs.kafka.error-handling.back-off.multiplier=3",
            "cqrs.kafka.error-handling.back-off.max-interval=2s",
            "cqrs.kafka.error-handling.dead-letter.partitions=2",
            "cqrs.kafka.error-handling.dead-letter.replicas=3")
        .run(
            context -> {
              KafkaCqrsProperties.ErrorHandlingProperties errorHandling =
                  context.getBean(KafkaCqrsProperties.class).getErrorHandling();
              assertThat(errorHandling.getMaxAttempts()).isEqualTo(5);
              assertThat(errorHandling.getBackOff().getInitialInterval())
                  .isEqualTo(Duration.ofMillis(250));
              assertThat(errorHandling.getBackOff().getMultiplier()).isEqualTo(3.0);
              assertThat(errorHandling.getBackOff().getMaxInterval())
                  .isEqualTo(Duration.ofSeconds(2));
              NewTopic deadLetterTopic =
                  context.getBean("cqrsEventsDeadLetterTopic", NewTopic.class);
              assertThat(deadLetterTopic.numPartitions()).isEqualTo(2);
              assertThat(deadLetterTopic.replicationFactor()).isEqualTo((short) 3);
            });
  }

  @Test
  void shouldFailToStartWithFewerThanOneAttempt() {
    contextRunner
        .withPropertyValues("cqrs.kafka.error-handling.max-attempts=0")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("cqrs.kafka.error-handling.max-attempts"));
  }

  private static void assertNoDeadLetterTopics(
      org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
    assertThat(context).doesNotHaveBean("cqrsCommandsDeadLetterTopic");
    assertThat(context).doesNotHaveBean("cqrsEventsDeadLetterTopic");
    assertThat(context).doesNotHaveBean("cqrsQueriesDeadLetterTopic");
  }

  @Test
  void remoteBusesRunTheOutboundMiddlewareBeansBeforeSending() {
    contextRunner
        .withBean("outboundBlocker", BusMiddleware.class, OutboundBlocker::new)
        .run(
            context -> {
              assertThatThrownBy(
                      () -> context.getBean(KafkaCommandBus.class).dispatch(new TestCommand("v")))
                  .hasMessage(OutboundBlocker.MESSAGE);
              assertThatThrownBy(() -> context.getBean(KafkaQueryBus.class).ask(new TestQuery("v")))
                  .hasMessage(OutboundBlocker.MESSAGE);
              assertThatThrownBy(
                      () -> context.getBean(KafkaEventBus.class).publish(new TestEvent("v")))
                  .hasMessage(OutboundBlocker.MESSAGE);
            });
  }
}
