package com.borjaglez.cqrs.rabbitmq.consumer;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

public class RabbitMqQueryConsumer extends RabbitMqConsumer {

  private final QueryHandlerRegistry registry;

  public RabbitMqQueryConsumer(
      QueryHandlerRegistry registry,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy namingStrategy) {
    super(rabbitTemplate, namingStrategy);
    this.registry = registry;
  }

  public Object consume(Message message, Query query) {
    return registry.handle(query);
  }
}
