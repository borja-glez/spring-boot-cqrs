package com.borjaglez.cqrs.rabbitmq.consumer;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

public class RabbitMqEventConsumer extends RabbitMqConsumer {

  private final EventHandlerRegistry registry;
  private final String exchangeName;
  private final String appName;

  public RabbitMqEventConsumer(
      EventHandlerRegistry registry,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy namingStrategy,
      String exchangeName,
      String appName) {
    super(rabbitTemplate, namingStrategy);
    this.registry = registry;
    this.exchangeName = exchangeName;
    this.appName = appName;
  }

  public void consume(Message message, Event event) {
    try {
      registry.handle(event);
    } catch (Exception e) {
      handleConsumptionError(message, exchangeName, appName);
    }
  }
}
