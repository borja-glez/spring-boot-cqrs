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

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.kafka.KafkaCommandBus;
import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.consumer.KafkaCommandConsumer;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.serialization.MessageSerializer;

@AutoConfiguration
@AutoConfigureAfter(KafkaCqrsAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnBean(CommandHandlerRegistry.class)
@ConditionalOnProperty(prefix = "cqrs.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaCommandBusAutoConfiguration {

  @Bean(name = "cqrsCommandsTopic")
  @ConditionalOnProperty(prefix = "cqrs.kafka", name = "auto-create-topics", havingValue = "true", matchIfMissing = true)
  public NewTopic cqrsCommandsTopic(
      KafkaCqrsProperties properties, KafkaTopicNamingStrategy kafkaTopicNamingStrategy) {
    return new NewTopic(
        kafkaTopicNamingStrategy.topic(properties.getCommands().getTopic()),
        properties.getCommands().getPartitions(),
        properties.getCommands().getReplicas());
  }

  @Bean
  public KafkaCommandBus kafkaCommandBus(
      KafkaMessagePublisher kafkaMessagePublisher,
      KafkaRequestReplyClient kafkaRequestReplyClient,
      KafkaTopicNamingStrategy kafkaTopicNamingStrategy,
      KafkaCqrsProperties properties) {
    return new KafkaCommandBus(
        kafkaMessagePublisher,
        kafkaRequestReplyClient,
        kafkaTopicNamingStrategy,
        properties.getCommands().getTopic());
  }

  @Bean
  public KafkaCommandConsumer kafkaCommandConsumer(
      CommandHandlerRegistry commandHandlerRegistry,
      ObjectProvider<List<BusMiddleware>> middlewaresProvider,
      MessageSerializer messageSerializer,
      KafkaMessagePublisher kafkaMessagePublisher) {
    return new KafkaCommandConsumer(
        commandHandlerRegistry,
        middlewaresProvider.getIfAvailable(Collections::emptyList),
        messageSerializer,
        kafkaMessagePublisher);
  }

  @Bean(name = "cqrsCommandListenerContainer")
  public ConcurrentMessageListenerContainer<String, byte[]> cqrsCommandListenerContainer(
      ConsumerFactory<String, byte[]> cqrsKafkaConsumerFactory,
      KafkaCommandConsumer kafkaCommandConsumer,
      KafkaCqrsProperties properties,
      KafkaTopicNamingStrategy kafkaTopicNamingStrategy,
      @Value("${spring.application.name:cqrs-app}") String applicationName) {
    ContainerProperties containerProperties =
        new ContainerProperties(kafkaTopicNamingStrategy.topic(properties.getCommands().getTopic()));
    containerProperties.setGroupId(resolveGroupId(properties.getCommands().getGroupId(), applicationName, "commands"));
    containerProperties.setMessageListener((MessageListener<String, byte[]>) kafkaCommandConsumer::consume);
    ConcurrentMessageListenerContainer<String, byte[]> container =
        new ConcurrentMessageListenerContainer<>(cqrsKafkaConsumerFactory, containerProperties);
    container.setConcurrency(properties.getCommands().getConcurrency());
    container.getContainerProperties().setMissingTopicsFatal(false);
    return container;
  }

  private String resolveGroupId(String configuredGroupId, String applicationName, String suffix) {
    return configuredGroupId == null || configuredGroupId.isBlank()
        ? applicationName + ".cqrs." + suffix
        : configuredGroupId;
  }
}
