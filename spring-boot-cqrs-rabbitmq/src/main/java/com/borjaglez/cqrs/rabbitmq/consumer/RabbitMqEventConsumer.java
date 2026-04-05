package com.borjaglez.cqrs.rabbitmq.consumer;

import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

public class RabbitMqEventConsumer extends RabbitMqConsumer {

  private final EventHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;
  private final String exchangeName;
  private final String appName;

  public RabbitMqEventConsumer(
      EventHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy namingStrategy,
      String exchangeName,
      String appName) {
    super(rabbitTemplate, namingStrategy);
    this.registry = registry;
    this.middlewares = middlewares;
    this.exchangeName = exchangeName;
    this.appName = appName;
  }

  public void consume(Message message, Event event) {
    try {
      DefaultMiddlewareChain chain =
          new DefaultMiddlewareChain(
              middlewares,
              msg -> {
                registry.handle((Event) msg);
                return null;
              });
      chain.proceed(event);
    } catch (Exception e) {
      handleConsumptionError(message, exchangeName, appName);
    }
  }
}
