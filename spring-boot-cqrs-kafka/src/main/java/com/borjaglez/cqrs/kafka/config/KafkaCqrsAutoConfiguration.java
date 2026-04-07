package com.borjaglez.cqrs.kafka.config;

import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;

import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.infrastructure.DefaultKafkaPartitionKeyStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.DefaultKafkaTopicNamingStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaPartitionKeyStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;

@AutoConfiguration
@AutoConfigureAfter(name = "com.borjaglez.cqrs.autoconfigure.CqrsSerializationAutoConfiguration")
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(
    prefix = "cqrs.kafka",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(KafkaCqrsProperties.class)
public class KafkaCqrsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public KafkaTopicNamingStrategy kafkaTopicNamingStrategy(KafkaCqrsProperties properties) {
    return new DefaultKafkaTopicNamingStrategy(properties.getPrefix());
  }

  @Bean
  @ConditionalOnMissingBean
  public KafkaPartitionKeyStrategy kafkaPartitionKeyStrategy(
      KafkaCqrsProperties properties, MessageNamingStrategy messageNamingStrategy) {
    return new DefaultKafkaPartitionKeyStrategy(properties, messageNamingStrategy);
  }

  @Bean
  @ConditionalOnMissingBean(name = "cqrsKafkaProducerFactory")
  public ProducerFactory<String, byte[]> cqrsKafkaProducerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> properties = kafkaProperties.buildProducerProperties();
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    return new DefaultKafkaProducerFactory<>(properties);
  }

  @Bean
  @ConditionalOnMissingBean(name = "cqrsKafkaConsumerFactory")
  public ConsumerFactory<String, byte[]> cqrsKafkaConsumerFactory(KafkaProperties kafkaProperties) {
    Map<String, Object> properties = kafkaProperties.buildConsumerProperties();
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    properties.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return new DefaultKafkaConsumerFactory<>(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public KafkaTemplate<String, byte[]> cqrsKafkaTemplate(
      ProducerFactory<String, byte[]> cqrsKafkaProducerFactory) {
    return new KafkaTemplate<>(cqrsKafkaProducerFactory);
  }

  @Bean
  @ConditionalOnMissingBean
  public KafkaMessagePublisher kafkaMessagePublisher(
      KafkaTemplate<String, byte[]> cqrsKafkaTemplate,
      MessageSerializer messageSerializer,
      KafkaPartitionKeyStrategy kafkaPartitionKeyStrategy,
      MessageNamingStrategy messageNamingStrategy) {
    return new KafkaMessagePublisher(
        cqrsKafkaTemplate, messageSerializer, kafkaPartitionKeyStrategy, messageNamingStrategy);
  }

  @Bean
  @ConditionalOnMissingBean
  public KafkaRequestReplyClient kafkaRequestReplyClient(
      KafkaTemplate<String, byte[]> cqrsKafkaTemplate,
      MessageSerializer messageSerializer,
      KafkaPartitionKeyStrategy kafkaPartitionKeyStrategy,
      MessageNamingStrategy messageNamingStrategy,
      KafkaTopicNamingStrategy kafkaTopicNamingStrategy,
      KafkaCqrsProperties properties,
      @Value("${spring.application.name:cqrs-app}") String applicationName) {
    return new KafkaRequestReplyClient(
        cqrsKafkaTemplate,
        messageSerializer,
        kafkaPartitionKeyStrategy,
        messageNamingStrategy,
        kafkaTopicNamingStrategy.replyTopic(applicationName, properties.getReplies().getTopic()),
        properties.getReplies().getTimeout());
  }

  @Bean(name = "cqrsRepliesTopic")
  @ConditionalOnProperty(
      prefix = "cqrs.kafka",
      name = "auto-create-topics",
      havingValue = "true",
      matchIfMissing = true)
  public NewTopic cqrsRepliesTopic(
      KafkaCqrsProperties properties,
      KafkaTopicNamingStrategy kafkaTopicNamingStrategy,
      @Value("${spring.application.name:cqrs-app}") String applicationName) {
    return new NewTopic(
        kafkaTopicNamingStrategy.replyTopic(applicationName, properties.getReplies().getTopic()),
        properties.getReplies().getPartitions(),
        properties.getReplies().getReplicas());
  }

  @Bean(name = "cqrsKafkaReplyContainer")
  public ConcurrentMessageListenerContainer<String, byte[]> cqrsKafkaReplyContainer(
      ConsumerFactory<String, byte[]> cqrsKafkaConsumerFactory,
      KafkaRequestReplyClient kafkaRequestReplyClient,
      KafkaCqrsProperties properties,
      KafkaTopicNamingStrategy kafkaTopicNamingStrategy,
      @Value("${spring.application.name:cqrs-app}") String applicationName) {
    ContainerProperties containerProperties =
        new ContainerProperties(
            kafkaTopicNamingStrategy.replyTopic(
                applicationName, properties.getReplies().getTopic()));
    containerProperties.setGroupId(
        applicationName + ".cqrs.replies." + UUID.randomUUID().toString().replace('-', '.'));
    containerProperties.setMessageListener(
        (MessageListener<String, byte[]>) kafkaRequestReplyClient::handleReply);
    ConcurrentMessageListenerContainer<String, byte[]> container =
        new ConcurrentMessageListenerContainer<>(cqrsKafkaConsumerFactory, containerProperties);
    container.setConcurrency(1);
    container.getContainerProperties().setMissingTopicsFatal(false);
    return container;
  }
}
