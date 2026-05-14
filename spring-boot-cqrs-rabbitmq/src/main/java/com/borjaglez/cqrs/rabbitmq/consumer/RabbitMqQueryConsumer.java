package com.borjaglez.cqrs.rabbitmq.consumer;

import java.util.List;
import java.util.Objects;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

public class RabbitMqQueryConsumer extends RabbitMqConsumer {

  private final QueryHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;
  private final String contextHeaderPrefix;

  public RabbitMqQueryConsumer(
      QueryHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy namingStrategy) {
    this(
        registry,
        middlewares,
        rabbitTemplate,
        namingStrategy,
        RabbitMqPublisher.DEFAULT_CONTEXT_HEADER_PREFIX);
  }

  public RabbitMqQueryConsumer(
      QueryHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy namingStrategy,
      String contextHeaderPrefix) {
    super(rabbitTemplate, namingStrategy);
    this.registry = registry;
    this.middlewares = middlewares;
    this.contextHeaderPrefix =
        Objects.requireNonNullElse(
            contextHeaderPrefix, RabbitMqPublisher.DEFAULT_CONTEXT_HEADER_PREFIX);
  }

  public Object consume(Message message, Query query) {
    MessageContext incoming = RabbitMqContextHeaders.extract(message, contextHeaderPrefix);
    MessageContext.Scope scope = MessageContext.scope(incoming);
    try {
      DefaultMiddlewareChain chain =
          new DefaultMiddlewareChain(middlewares, msg -> registry.handle((Query) msg));
      return chain.proceed(query);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      scope.close();
    }
  }
}
