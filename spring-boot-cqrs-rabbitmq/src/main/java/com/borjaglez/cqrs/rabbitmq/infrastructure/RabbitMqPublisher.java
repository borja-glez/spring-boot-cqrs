package com.borjaglez.cqrs.rabbitmq.infrastructure;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

public class RabbitMqPublisher {

  private static final String HEADER_MESSAGE_TYPE = "cqrs.message.type";
  private static final String HEADER_ERROR = "cqrs.error";

  private final RabbitTemplate rabbitTemplate;

  public RabbitMqPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void publish(String exchange, String routingKey, Object message, String messageType) {
    rabbitTemplate.convertAndSend(
        exchange,
        routingKey,
        message,
        m -> {
          m.getMessageProperties().setHeader(HEADER_MESSAGE_TYPE, messageType);
          return m;
        });
  }

  public Object publishAndReceive(
      String exchange, String routingKey, Object payload, String messageType) {
    MessageConverter converter = rabbitTemplate.getMessageConverter();
    MessageProperties properties = new MessageProperties();
    properties.setHeader(HEADER_MESSAGE_TYPE, messageType);

    Message requestMessage = converter.toMessage(payload, properties);
    Message reply = rabbitTemplate.sendAndReceive(exchange, routingKey, requestMessage);

    if (reply == null) {
      return null;
    }

    checkError(reply);

    return converter.fromMessage(reply);
  }

  public void checkError(Message reply) {
    Object errorHeader = reply.getMessageProperties().getHeader(HEADER_ERROR);
    if (Boolean.TRUE.equals(errorHeader)) {
      String errorMessage = new String(reply.getBody());
      throw new RuntimeException("Remote handler error: " + errorMessage);
    }
  }
}
