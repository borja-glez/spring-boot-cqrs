package com.borjaglez.cqrs.rabbitmq.consumer;

import java.util.List;
import java.util.Objects;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

public class RabbitMqEventConsumer extends RabbitMqConsumer {

  private final EventHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;
  private final String exchangeName;
  private final String appName;
  private final String contextHeaderPrefix;

  public RabbitMqEventConsumer(
      EventHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy namingStrategy,
      String exchangeName,
      String appName) {
    this(
        registry,
        middlewares,
        rabbitTemplate,
        namingStrategy,
        exchangeName,
        appName,
        RabbitMqPublisher.DEFAULT_CONTEXT_HEADER_PREFIX);
  }

  public RabbitMqEventConsumer(
      EventHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy namingStrategy,
      String exchangeName,
      String appName,
      String contextHeaderPrefix) {
    super(rabbitTemplate, namingStrategy);
    this.registry = registry;
    this.middlewares = middlewares;
    this.exchangeName = exchangeName;
    this.appName = appName;
    this.contextHeaderPrefix =
        Objects.requireNonNullElse(
            contextHeaderPrefix, RabbitMqPublisher.DEFAULT_CONTEXT_HEADER_PREFIX);
  }

  public void consume(Message message, Event event) {
    MessageContext incoming = RabbitMqContextHeaders.extract(message, contextHeaderPrefix);
    MessageContext.Scope scope = MessageContext.scope(incoming);
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
    } finally {
      scope.close();
    }
  }
}
