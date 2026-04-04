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

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.consumer.RabbitMqCommandConsumer;
import com.borjaglez.cqrs.rabbitmq.infrastructure.ExtendedMessageListenerAdapter;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqBusDeclarationBuilder;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

@AutoConfiguration
@AutoConfigureAfter(RabbitMqCqrsAutoConfiguration.class)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnBean(CommandHandlerRegistry.class)
@ConditionalOnProperty(
    prefix = "cqrs.rabbitmq",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RabbitMqCommandBusAutoConfiguration {

  @Bean
  public Declarables cqrsCommandDeclarables(
      RabbitMqBusDeclarationBuilder builder,
      RabbitMqCqrsProperties properties,
      CommandHandlerRegistry registry,
      MessageNamingStrategy messageNaming,
      @Value("${spring.application.name:cqrs-app}") String appName) {
    List<String> routingKeys =
        new ArrayList<>(
            registry.getRegisteredCommands().stream().map(messageNaming::commandName).toList());
    return builder.buildWithRetryAndDeadLetter(
        appName,
        properties.getCommands().getExchange(),
        routingKeys,
        properties.getRetry().getTtl());
  }

  @Bean
  public RabbitMqCommandBus rabbitMqCommandBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      RabbitMqCqrsProperties properties) {
    return new RabbitMqCommandBus(
        publisher, rabbitNaming, messageNaming, properties.getCommands().getExchange());
  }

  @Bean
  public SimpleMessageListenerContainer cqrsCommandListenerContainer(
      ConnectionFactory connectionFactory,
      RabbitMqCqrsProperties properties,
      CommandHandlerRegistry registry,
      RabbitTemplate rabbitTemplate,
      @Qualifier("cqrsMessageConverter") MessageConverter messageConverter,
      RabbitMqNamingStrategy rabbitNaming,
      @Value("${spring.application.name:cqrs-app}") String appName) {
    RabbitMqCommandConsumer consumer =
        new RabbitMqCommandConsumer(
            registry,
            rabbitTemplate,
            rabbitNaming,
            properties.getCommands().getExchange(),
            appName);

    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(consumer, messageConverter, "consume");

    SimpleMessageListenerContainer container =
        new SimpleMessageListenerContainer(connectionFactory);
    container.setMessageListener(adapter);
    container.setQueueNames(rabbitNaming.queue(appName, properties.getCommands().getExchange()));
    container.setConcurrentConsumers(properties.getCommands().getConcurrentConsumers());
    container.setMaxConcurrentConsumers(properties.getCommands().getMaxConcurrentConsumers());
    return container;
  }
}
