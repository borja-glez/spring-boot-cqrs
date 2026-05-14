package com.borjaglez.cqrs.rabbitmq.consumer;

import java.util.List;
import java.util.Objects;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

public class RabbitMqCommandConsumer extends RabbitMqConsumer {

  private static final String HEADER_MESSAGE_TYPE = "cqrs.message.type";

  private final CommandHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;
  private final String exchangeName;
  private final String appName;
  private final String contextHeaderPrefix;

  public RabbitMqCommandConsumer(
      CommandHandlerRegistry registry,
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

  public RabbitMqCommandConsumer(
      CommandHandlerRegistry registry,
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

  public Object consume(Message message, Command command) {
    String messageType = getMessageType(message);
    MessageContext incoming = RabbitMqContextHeaders.extract(message, contextHeaderPrefix);
    MessageContext.Scope scope = MessageContext.scope(incoming);
    try {
      DefaultMiddlewareChain chain =
          new DefaultMiddlewareChain(middlewares, msg -> registry.handle((Command) msg));
      Object result = chain.proceed(command);

      if ("command_reply".equals(messageType)) {
        return result;
      }
      if ("command_wait".equals(messageType)) {
        return "";
      }
      return null;
    } catch (RuntimeException e) {
      if ("command_reply".equals(messageType) || "command_wait".equals(messageType)) {
        throw e;
      }
      handleConsumptionError(message, exchangeName, appName);
      return null;
    } catch (Exception e) {
      if ("command_reply".equals(messageType) || "command_wait".equals(messageType)) {
        throw new RuntimeException(e);
      }
      handleConsumptionError(message, exchangeName, appName);
      return null;
    } finally {
      scope.close();
    }
  }

  private String getMessageType(Message message) {
    Object header = message.getMessageProperties().getHeader(HEADER_MESSAGE_TYPE);
    return header != null ? header.toString() : "command";
  }
}
