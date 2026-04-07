package com.borjaglez.cqrs.kafka.config;

import java.util.Collections;
import java.util.List;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;

import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.kafka.KafkaEventBus;
import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.consumer.KafkaEventConsumer;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.serialization.MessageSerializer;

@AutoConfiguration
@AutoConfigureAfter(KafkaCqrsAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnBean(EventHandlerRegistry.class)
@ConditionalOnProperty(prefix = "cqrs.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaEventBusAutoConfiguration {

  @Bean(name = "cqrsEventsTopic")
  @ConditionalOnProperty(prefix = "cqrs.kafka", name = "auto-create-topics", havingValue = "true", matchIfMissing = true)
  public NewTopic cqrsEventsTopic(
      KafkaCqrsProperties properties, KafkaTopicNamingStrategy kafkaTopicNamingStrategy) {
    return new NewTopic(
        kafkaTopicNamingStrategy.topic(properties.getEvents().getTopic()),
        properties.getEvents().getPartitions(),
        properties.getEvents().getReplicas());
  }

  @Bean
  public KafkaEventBus kafkaEventBus(
      KafkaMessagePublisher kafkaMessagePublisher,
      KafkaTopicNamingStrategy kafkaTopicNamingStrategy,
      KafkaCqrsProperties properties,
      @Qualifier("springEventBus") EventBus fallbackEventBus) {
    return new KafkaEventBus(
        kafkaMessagePublisher,
        kafkaTopicNamingStrategy,
        properties.getEvents().getTopic(),
        fallbackEventBus);
  }

  @Bean
  public KafkaEventConsumer kafkaEventConsumer(
      EventHandlerRegistry eventHandlerRegistry,
      ObjectProvider<List<BusMiddleware>> middlewaresProvider,
      MessageSerializer messageSerializer) {
    return new KafkaEventConsumer(
        eventHandlerRegistry,
        middlewaresProvider.getIfAvailable(Collections::emptyList),
        messageSerializer);
  }

  @Bean(name = "cqrsEventListenerContainer")
  public ConcurrentMessageListenerContainer<String, byte[]> cqrsEventListenerContainer(
      ConsumerFactory<String, byte[]> cqrsKafkaConsumerFactory,
      KafkaEventConsumer kafkaEventConsumer,
      KafkaCqrsProperties properties,
      KafkaTopicNamingStrategy kafkaTopicNamingStrategy,
      @Value("${spring.application.name:cqrs-app}") String applicationName) {
    ContainerProperties containerProperties =
        new ContainerProperties(kafkaTopicNamingStrategy.topic(properties.getEvents().getTopic()));
    containerProperties.setGroupId(resolveGroupId(properties.getEvents().getGroupId(), applicationName, "events"));
    containerProperties.setMessageListener((MessageListener<String, byte[]>) kafkaEventConsumer::consume);
    ConcurrentMessageListenerContainer<String, byte[]> container =
        new ConcurrentMessageListenerContainer<>(cqrsKafkaConsumerFactory, containerProperties);
    container.setConcurrency(properties.getEvents().getConcurrency());
    container.getContainerProperties().setMissingTopicsFatal(false);
    return container;
  }

  private String resolveGroupId(String configuredGroupId, String applicationName, String suffix) {
    return configuredGroupId == null || configuredGroupId.isBlank()
        ? applicationName + ".cqrs." + suffix
        : configuredGroupId;
  }
}
