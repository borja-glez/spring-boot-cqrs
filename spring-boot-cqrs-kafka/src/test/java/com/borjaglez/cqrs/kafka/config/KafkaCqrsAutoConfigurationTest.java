package com.borjaglez.cqrs.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
