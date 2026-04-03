package com.borjaglez.cqrs.rabbitmq.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqEventConsumer;
import com.borjaglez.cqrs.rabbitmq.infrastructure.ExtendedMessageListenerAdapter;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

@AutoConfiguration
@AutoConfigureAfter(RabbitMqCqrsAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnBean(EventHandlerRegistry.class)
@ConditionalOnProperty(
    prefix = "cqrs.rabbitmq",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RabbitMqEventBusAutoConfiguration {

  @Bean
  public Declarables cqrsEventDeclarables(
      RabbitMqBusDeclarationBuilder builder,
      RabbitMqCqrsProperties properties,
      EventHandlerRegistry registry,
      MessageNamingStrategy messageNaming,
      @Value("${spring.application.name:cqrs-app}") String appName) {
    List<String> routingKeys =
        new ArrayList<>(
            registry.getRegisteredEvents().stream().map(messageNaming::eventName).toList());
    return builder.buildWithRetryAndDeadLetter(
        appName, properties.getEvents().getExchange(), routingKeys, properties.getRetry().getTtl());
  }

  @Bean
  public RabbitMqEventBus rabbitMqEventBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      RabbitMqCqrsProperties properties,
      @Qualifier("springEventBus") EventBus fallbackEventBus) {
    return new RabbitMqEventBus(
        publisher,
        rabbitNaming,
        messageNaming,
        properties.getEvents().getExchange(),
        fallbackEventBus);
  }

  @Bean
  public SimpleMessageListenerContainer cqrsEventListenerContainer(
      ConnectionFactory connectionFactory,
      RabbitMqCqrsProperties properties,
      EventHandlerRegistry registry,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy rabbitNaming,
      @Value("${spring.application.name:cqrs-app}") String appName) {
    RabbitMqEventConsumer consumer =
        new RabbitMqEventConsumer(
            registry, rabbitTemplate, rabbitNaming, properties.getEvents().getExchange(), appName);

    Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(consumer, converter, "consume");

    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setMessageListener(adapter);
    container.setQueueNames(rabbitNaming.queue(appName, properties.getEvents().getExchange()));
    container.setConcurrentConsumers(properties.getEvents().getConcurrentConsumers());
    container.setMaxConcurrentConsumers(properties.getEvents().getMaxConcurrentConsumers());
    return container;
  }
}
