package com.borjaglez.cqrs.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;

import com.borjaglez.cqrs.autoconfigure.CqrsAutoConfiguration;
import com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.kafka.KafkaCommandBus;
import com.borjaglez.cqrs.kafka.KafkaEventBus;
import com.borjaglez.cqrs.kafka.KafkaQueryBus;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaPartitionKeyStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

class KafkaCqrsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "spring.application.name=orders-service",
              "spring.kafka.bootstrap-servers=localhost:9092")
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
  void shouldRespectCustomPrefixAndFallbackEventBus() {
    contextRunner
        .withPropertyValues("cqrs.kafka.prefix=custom")
        .run(
            context -> {
              KafkaTopicNamingStrategy namingStrategy =
                  context.getBean(KafkaTopicNamingStrategy.class);
              assertThat(namingStrategy.topic("commands")).isEqualTo("custom.commands");
              assertThat(context.getBean(KafkaEventBus.class)).isNotNull();
              assertThat(context.getBean("springEventBus", EventBus.class)).isNotNull();
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
}
