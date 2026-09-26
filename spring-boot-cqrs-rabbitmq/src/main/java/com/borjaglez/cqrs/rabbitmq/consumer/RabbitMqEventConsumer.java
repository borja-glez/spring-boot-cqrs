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
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqExposure;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

public class RabbitMqEventConsumer extends RabbitMqConsumer {

  private final EventHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;
  private final String exchangeName;
  private final String appName;
  private final String contextHeaderPrefix;
  private final RabbitMqExposure exposure;

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
    this(
        registry,
        middlewares,
        rabbitTemplate,
        namingStrategy,
        exchangeName,
        appName,
        contextHeaderPrefix,
        DEFAULT_MAX_ATTEMPTS);
  }

  /**
   * Creates the consumer. {@code maxAttempts} is the total number of deliveries of a failed
   * message, including the first one, and must be at least 1. Only events annotated with {@code
   * CqrsMessage} are accepted ({@link RabbitMqExposure#ANNOTATED}).
   */
  public RabbitMqEventConsumer(
      EventHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy namingStrategy,
      String exchangeName,
      String appName,
      String contextHeaderPrefix,
      int maxAttempts) {
    this(
        registry,
        middlewares,
        rabbitTemplate,
        namingStrategy,
        exchangeName,
        appName,
        contextHeaderPrefix,
        maxAttempts,
        RabbitMqExposure.ANNOTATED);
  }

  /**
   * Creates the consumer. {@code maxAttempts} is the total number of deliveries of a failed
   * message, including the first one, and must be at least 1. An event that {@code exposure} does
   * not expose is rejected without requeue and never handled; for an exposed event only the
   * handlers not marked {@code remote = false} run.
   */
  public RabbitMqEventConsumer(
      EventHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy namingStrategy,
      String exchangeName,
      String appName,
      String contextHeaderPrefix,
      int maxAttempts,
      RabbitMqExposure exposure) {
    super(rabbitTemplate, namingStrategy, maxAttempts);
    this.exposure = exposure;
    this.registry = registry;
    this.middlewares = middlewares;
    this.exchangeName = exchangeName;
    this.appName = appName;
    this.contextHeaderPrefix =
        Objects.requireNonNullElse(
            contextHeaderPrefix, RabbitMqPublisher.DEFAULT_CONTEXT_HEADER_PREFIX);
  }

  public void consume(Message message, Event event) {
    if (!exposure.exposesEvent(registry, event.getClass())) {
      throw rejectNotExposed(message, event.getClass());
    }
    MessageContext incoming = RabbitMqContextHeaders.extract(message, contextHeaderPrefix);
    MessageContext.Scope scope = MessageContext.scope(incoming);
    try {
      DefaultMiddlewareChain chain =
          new DefaultMiddlewareChain(
              middlewares,
              msg -> {
                registry.handleRemote((Event) msg);
                return null;
              });
      chain.proceed(event);
    } catch (Exception e) {
      handleConsumptionError(message, exchangeName, appName, e);
    } finally {
      scope.close();
    }
  }
}
