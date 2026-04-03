package com.borjaglez.cqrs.rabbitmq.consumer;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

public class RabbitMqCommandConsumer extends RabbitMqConsumer {

  private static final String HEADER_MESSAGE_TYPE = "cqrs.message.type";

  private final CommandHandlerRegistry registry;
  private final String exchangeName;
  private final String appName;

  public RabbitMqCommandConsumer(
      CommandHandlerRegistry registry,
      RabbitTemplate rabbitTemplate,
      RabbitMqNamingStrategy namingStrategy,
      String exchangeName,
      String appName) {
    super(rabbitTemplate, namingStrategy);
    this.registry = registry;
    this.exchangeName = exchangeName;
    this.appName = appName;
  }

  public Object consume(Message message, Command command) {
    String messageType = getMessageType(message);

    try {
      Object result = registry.handle(command);

      if ("command_reply".equals(messageType)) {
        return result;
      }
      if ("command_wait".equals(messageType)) {
        return "OK";
      }
      return null;
    } catch (Exception e) {
      if ("command_reply".equals(messageType) || "command_wait".equals(messageType)) {
        throw e;
      }
      handleConsumptionError(message, exchangeName, appName);
      return null;
    }
  }

  private String getMessageType(Message message) {
    Object header = message.getMessageProperties().getHeader(HEADER_MESSAGE_TYPE);
    return header != null ? header.toString() : "command";
  }
}
