package com.borjaglez.cqrs.rabbitmq.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqQueryConsumer;
import com.borjaglez.cqrs.rabbitmq.infrastructure.ExtendedMessageListenerAdapter;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

@AutoConfiguration
@AutoConfigureAfter(RabbitMqCqrsAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnBean(QueryHandlerRegistry.class)
@ConditionalOnProperty(
    prefix = "cqrs.rabbitmq",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RabbitMqQueryBusAutoConfiguration {

  @Bean
  public Declarables cqrsQueryDeclarables(
      RabbitMqBusDeclarationBuilder builder,
      RabbitMqCqrsProperties properties,
      QueryHandlerRegistry registry,
      MessageNamingStrategy messageNaming,
      @Value("${spring.application.name:cqrs-app}") String appName) {
    List<String> routingKeys =
        new ArrayList<>(
            registry.getRegisteredQueries().stream().map(messageNaming::queryName).toList());
    return builder.buildSimple(appName, properties.getQueries().getExchange(), routingKeys);
  }

  @Bean
  public RabbitMqQueryBus rabbitMqQueryBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      RabbitMqCqrsProperties properties) {
    return new RabbitMqQueryBus(
        publisher, rabbitNaming, messageNaming, properties.getQueries().getExchange());
  }

  @Bean
  public SimpleMessageListenerContainer cqrsQueryListenerContainer(
      ConnectionFactory connectionFactory,
      RabbitMqCqrsProperties properties,
      QueryHandlerRegistry registry,
      RabbitTemplate rabbitTemplate,
      @Qualifier("cqrsMessageConverter") MessageConverter messageConverter,
      RabbitMqNamingStrategy rabbitNaming,
      @Value("${spring.application.name:cqrs-app}") String appName) {
    RabbitMqQueryConsumer consumer =
        new RabbitMqQueryConsumer(registry, rabbitTemplate, rabbitNaming);

    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(consumer, messageConverter, "consume");

    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setMessageListener(adapter);
    container.setQueueNames(rabbitNaming.queue(appName, properties.getQueries().getExchange()));
    container.setConcurrentConsumers(properties.getQueries().getConcurrentConsumers());
    container.setMaxConcurrentConsumers(properties.getQueries().getMaxConcurrentConsumers());
    return container;
  }
}
