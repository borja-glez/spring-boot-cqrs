package com.borjaglez.cqrs.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;

import com.borjaglez.cqrs.kafka.consumer.KafkaCommandConsumer;
import com.borjaglez.cqrs.kafka.consumer.KafkaEventConsumer;
import com.borjaglez.cqrs.kafka.consumer.KafkaQueryConsumer;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaReplyListener;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;

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
                "orders-service",
                false,
                mock(KafkaTemplate.class),
                noErrorHandler(),
                noErrorHandler());

    assertThat(container.getContainerProperties().getGroupId())
        .isEqualTo("orders-service.cqrs.commands");
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
                "orders-service",
                false,
                mock(KafkaTemplate.class),
                noErrorHandler(),
                noErrorHandler());

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
                "orders-service",
                false,
                mock(KafkaTemplate.class),
                noErrorHandler(),
                noErrorHandler());

    assertThat(container.getContainerProperties().getGroupId())
        .isEqualTo("orders-service.cqrs.queries");
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
                "orders-service",
                false,
                mock(KafkaTemplate.class),
                noErrorHandler(),
                noErrorHandler());

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
                "orders-service",
                false,
                mock(KafkaTemplate.class),
                noErrorHandler(),
                noErrorHandler());

    assertThat(container.getContainerProperties().getGroupId())
        .isEqualTo("orders-service.cqrs.events");
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
                "orders-service",
                false,
                mock(KafkaTemplate.class),
                noErrorHandler(),
                noErrorHandler());

    assertThat(container.getContainerProperties().getGroupId()).isEqualTo("custom-events");
  }

  @Test
  void replyContainerStartsAtLatestWithRandomGroupAndSeekingListener() {
    KafkaTopicNamingStrategy namingStrategy = mock(KafkaTopicNamingStrategy.class);
    when(namingStrategy.replyTopic("orders-service", "replies"))
        .thenReturn("cqrs.orders-service.replies");

    var container =
        new KafkaCqrsAutoConfiguration()
            .cqrsKafkaReplyContainer(
                mock(ConsumerFactory.class),
                mock(KafkaRequestReplyClient.class),
                new KafkaCqrsProperties(),
                namingStrategy,
                "orders-service",
                false);

    assertThat(container.getContainerProperties().getKafkaConsumerProperties())
        .containsEntry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    assertThat(container.getContainerProperties().getGroupId())
        .startsWith("orders-service.cqrs.replies.");
    assertThat(container.getContainerProperties().getMessageListener())
        .isInstanceOf(KafkaReplyListener.class);
  }

  @Test
  void commandEventAndQueryContainersKeepTheConsumerFactoryOffsetReset() {
    KafkaCqrsProperties properties = new KafkaCqrsProperties();
    KafkaTopicNamingStrategy namingStrategy = mock(KafkaTopicNamingStrategy.class);
    when(namingStrategy.topic(anyString())).thenReturn("cqrs.topic");

    var commands =
        new KafkaCommandBusAutoConfiguration()
            .cqrsCommandListenerContainer(
                mock(ConsumerFactory.class),
                mock(KafkaCommandConsumer.class),
                properties,
                namingStrategy,
                "orders-service",
                false,
                mock(KafkaTemplate.class),
                noErrorHandler(),
                noErrorHandler());
    var events =
        new KafkaEventBusAutoConfiguration()
            .cqrsEventListenerContainer(
                mock(ConsumerFactory.class),
                mock(KafkaEventConsumer.class),
                properties,
                namingStrategy,
                "orders-service",
                false,
                mock(KafkaTemplate.class),
                noErrorHandler(),
                noErrorHandler());
    var queries =
        new KafkaQueryBusAutoConfiguration()
            .cqrsQueryListenerContainer(
                mock(ConsumerFactory.class),
                mock(KafkaQueryConsumer.class),
                properties,
                namingStrategy,
                "orders-service",
                false,
                mock(KafkaTemplate.class),
                noErrorHandler(),
                noErrorHandler());

    assertThat(commands.getContainerProperties().getKafkaConsumerProperties())
        .doesNotContainKey(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
    assertThat(events.getContainerProperties().getKafkaConsumerProperties())
        .doesNotContainKey(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
    assertThat(queries.getContainerProperties().getKafkaConsumerProperties())
        .doesNotContainKey(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<CommonErrorHandler> noErrorHandler() {
    return mock(ObjectProvider.class);
  }
}
