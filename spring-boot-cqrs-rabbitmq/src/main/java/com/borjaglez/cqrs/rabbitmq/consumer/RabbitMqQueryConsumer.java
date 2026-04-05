package com.borjaglez.cqrs.rabbitmq.consumer;

import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

public class RabbitMqQueryConsumer extends RabbitMqConsumer {

  private final QueryHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;

  public RabbitMqQueryConsumer(
      QueryHandlerRegistry registry,
      List<BusMiddleware> middlewares,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy namingStrategy) {
    super(rabbitTemplate, namingStrategy);
    this.registry = registry;
    this.middlewares = middlewares;
  }

  public Object consume(Message message, Query query) {
    DefaultMiddlewareChain chain =
        new DefaultMiddlewareChain(middlewares, msg -> registry.handle((Query) msg));
    try {
      return chain.proceed(query);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
