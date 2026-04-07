package com.borjaglez.cqrs.kafka.config;

import java.util.Collections;
import java.util.List;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
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

import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.KafkaQueryBus;
import com.borjaglez.cqrs.kafka.consumer.KafkaQueryConsumer;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.serialization.MessageSerializer;

@AutoConfiguration
@AutoConfigureAfter(KafkaCqrsAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnBean(QueryHandlerRegistry.class)
@ConditionalOnProperty(
    prefix = "cqrs.kafka",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class KafkaQueryBusAutoConfiguration {

  @Bean(name = "cqrsQueriesTopic")
  @ConditionalOnProperty(
      prefix = "cqrs.kafka",
      name = "auto-create-topics",
      havingValue = "true",
      matchIfMissing = true)
  public NewTopic cqrsQueriesTopic(
      KafkaCqrsProperties properties, KafkaTopicNamingStrategy kafkaTopicNamingStrategy) {
    return new NewTopic(
        kafkaTopicNamingStrategy.topic(properties.getQueries().getTopic()),
        properties.getQueries().getPartitions(),
        properties.getQueries().getReplicas());
  }

  @Bean
  public KafkaQueryBus kafkaQueryBus(
      KafkaRequestReplyClient kafkaRequestReplyClient,
      KafkaTopicNamingStrategy kafkaTopicNamingStrategy,
      KafkaCqrsProperties properties) {
    return new KafkaQueryBus(
        kafkaRequestReplyClient, kafkaTopicNamingStrategy, properties.getQueries().getTopic());
  }

  @Bean
  public KafkaQueryConsumer kafkaQueryConsumer(
      QueryHandlerRegistry queryHandlerRegistry,
      ObjectProvider<List<BusMiddleware>> middlewaresProvider,
      MessageSerializer messageSerializer,
      KafkaMessagePublisher kafkaMessagePublisher) {
    return new KafkaQueryConsumer(
        queryHandlerRegistry,
        middlewaresProvider.getIfAvailable(Collections::emptyList),
        messageSerializer,
        kafkaMessagePublisher);
  }

  @Bean(name = "cqrsQueryListenerContainer")
  public ConcurrentMessageListenerContainer<String, byte[]> cqrsQueryListenerContainer(
      ConsumerFactory<String, byte[]> cqrsKafkaConsumerFactory,
      KafkaQueryConsumer kafkaQueryConsumer,
      KafkaCqrsProperties properties,
      KafkaTopicNamingStrategy kafkaTopicNamingStrategy,
      @Value("${spring.application.name:cqrs-app}") String applicationName) {
    ContainerProperties containerProperties =
        new ContainerProperties(kafkaTopicNamingStrategy.topic(properties.getQueries().getTopic()));
    containerProperties.setGroupId(
        resolveGroupId(properties.getQueries().getGroupId(), applicationName, "queries"));
    containerProperties.setMessageListener(
        (MessageListener<String, byte[]>) kafkaQueryConsumer::consume);
    ConcurrentMessageListenerContainer<String, byte[]> container =
        new ConcurrentMessageListenerContainer<>(cqrsKafkaConsumerFactory, containerProperties);
    container.setConcurrency(properties.getQueries().getConcurrency());
    container.getContainerProperties().setMissingTopicsFatal(false);
    return container;
  }

  private String resolveGroupId(String configuredGroupId, String applicationName, String suffix) {
    return configuredGroupId == null || configuredGroupId.isBlank()
        ? applicationName + ".cqrs." + suffix
        : configuredGroupId;
  }
}
