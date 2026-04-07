package com.borjaglez.cqrs.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.kafka.consumer.KafkaCommandConsumer;
import com.borjaglez.cqrs.kafka.consumer.KafkaEventConsumer;
import com.borjaglez.cqrs.kafka.consumer.KafkaQueryConsumer;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.serialization.MessageSerializer;

class KafkaConfigurationCoverageTest {

  @Test
  void busPropertiesDefaultConstructorUsesExpectedDefaults() {
    KafkaCqrsProperties.BusProperties properties = new KafkaCqrsProperties.BusProperties();

    assertThat(properties.getTopic()).isEmpty();
    assertThat(properties.getPartitions()).isEqualTo(3);
    assertThat(properties.getReplicas()).isEqualTo((short) 1);
    assertThat(properties.getConcurrency()).isEqualTo(1);
    assertThat(properties.getGroupId()).isEmpty();
  }

  @Test
  void commandListenerContainerUsesGeneratedGroupIdWhenNull() {
    KafkaCqrsProperties properties = new KafkaCqrsProperties();
    properties.getCommands().setGroupId(null);
    KafkaTopicNamingStrategy namingStrategy = mock(KafkaTopicNamingStrategy.class);
    when(namingStrategy.topic("commands")).thenReturn("cqrs.commands");

    var container =
        new KafkaCommandBusAutoConfiguration()
            .cqrsCommandListenerContainer(
                mock(ConsumerFactory.class),
                mock(KafkaCommandConsumer.class),
                properties,
                namingStrategy,
                "orders-service");

    assertThat(container.getContainerProperties().getGroupId()).isEqualTo("orders-service.cqrs.commands");
  }

  @Test
  void commandListenerContainerUsesConfiguredGroupIdWhenPresent() {
    KafkaCqrsProperties properties = new KafkaCqrsProperties();
    properties.getCommands().setGroupId("custom-commands");
    KafkaTopicNamingStrategy namingStrategy = mock(KafkaTopicNamingStrategy.class);
    when(namingStrategy.topic("commands")).thenReturn("cqrs.commands");

    var container =
        new KafkaCommandBusAutoConfiguration()
            .cqrsCommandListenerContainer(
                mock(ConsumerFactory.class),
                mock(KafkaCommandConsumer.class),
                properties,
                namingStrategy,
                "orders-service");

    assertThat(container.getContainerProperties().getGroupId()).isEqualTo("custom-commands");
  }

  @Test
  void queryListenerContainerUsesGeneratedGroupIdWhenNull() {
    KafkaCqrsProperties properties = new KafkaCqrsProperties();
    properties.getQueries().setGroupId(null);
    KafkaTopicNamingStrategy namingStrategy = mock(KafkaTopicNamingStrategy.class);
    when(namingStrategy.topic("queries")).thenReturn("cqrs.queries");

    var container =
        new KafkaQueryBusAutoConfiguration()
            .cqrsQueryListenerContainer(
                mock(ConsumerFactory.class),
                mock(KafkaQueryConsumer.class),
                properties,
                namingStrategy,
                "orders-service");

    assertThat(container.getContainerProperties().getGroupId()).isEqualTo("orders-service.cqrs.queries");
  }

  @Test
  void queryListenerContainerUsesConfiguredGroupIdWhenPresent() {
    KafkaCqrsProperties properties = new KafkaCqrsProperties();
    properties.getQueries().setGroupId("custom-queries");
    KafkaTopicNamingStrategy namingStrategy = mock(KafkaTopicNamingStrategy.class);
    when(namingStrategy.topic("queries")).thenReturn("cqrs.queries");

    var container =
        new KafkaQueryBusAutoConfiguration()
            .cqrsQueryListenerContainer(
                mock(ConsumerFactory.class),
                mock(KafkaQueryConsumer.class),
                properties,
                namingStrategy,
                "orders-service");

    assertThat(container.getContainerProperties().getGroupId()).isEqualTo("custom-queries");
  }

  @Test
  void eventListenerContainerUsesGeneratedGroupIdWhenNull() {
    KafkaCqrsProperties properties = new KafkaCqrsProperties();
    properties.getEvents().setGroupId(null);
    KafkaTopicNamingStrategy namingStrategy = mock(KafkaTopicNamingStrategy.class);
    when(namingStrategy.topic("events")).thenReturn("cqrs.events");

    var container =
        new KafkaEventBusAutoConfiguration()
            .cqrsEventListenerContainer(
                mock(ConsumerFactory.class),
                mock(KafkaEventConsumer.class),
                properties,
                namingStrategy,
                "orders-service");

    assertThat(container.getContainerProperties().getGroupId()).isEqualTo("orders-service.cqrs.events");
  }

  @Test
  void eventListenerContainerUsesConfiguredGroupIdWhenPresent() {
    KafkaCqrsProperties properties = new KafkaCqrsProperties();
    properties.getEvents().setGroupId("custom-events");
    KafkaTopicNamingStrategy namingStrategy = mock(KafkaTopicNamingStrategy.class);
    when(namingStrategy.topic("events")).thenReturn("cqrs.events");

    var container =
        new KafkaEventBusAutoConfiguration()
            .cqrsEventListenerContainer(
                mock(ConsumerFactory.class),
                mock(KafkaEventConsumer.class),
                properties,
                namingStrategy,
                "orders-service");

    assertThat(container.getContainerProperties().getGroupId()).isEqualTo("custom-events");
  }
}
