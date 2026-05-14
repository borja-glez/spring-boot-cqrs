package com.borjaglez.cqrs.rabbitmq.infrastructure;

import java.util.Map;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;

public class RabbitMqPublisher {

  public static final String DEFAULT_CONTEXT_HEADER_PREFIX = "cqrs.context.";

  private static final String HEADER_MESSAGE_TYPE = "cqrs.message.type";
  private static final String HEADER_ERROR = "cqrs.error";

  private final RabbitTemplate rabbitTemplate;
  private final String contextHeaderPrefix;

  public RabbitMqPublisher(RabbitTemplate rabbitTemplate) {
    this(rabbitTemplate, DEFAULT_CONTEXT_HEADER_PREFIX);
  }

  public RabbitMqPublisher(RabbitTemplate rabbitTemplate, String contextHeaderPrefix) {
    this.rabbitTemplate = rabbitTemplate;
    this.contextHeaderPrefix =
        contextHeaderPrefix == null ? DEFAULT_CONTEXT_HEADER_PREFIX : contextHeaderPrefix;
  }

  public void publish(String exchange, String routingKey, Object message, String messageType) {
    Map<String, String> contextHeaders = snapshotContextHeaders();
    rabbitTemplate.convertAndSend(
        exchange,
        routingKey,
        message,
        m -> {
          applyCqrsHeaders(m.getMessageProperties(), messageType, contextHeaders);
          return m;
        });
  }

  public Object publishAndReceive(
      String exchange, String routingKey, Object payload, String messageType) {
    MessageConverter converter = rabbitTemplate.getMessageConverter();
    MessageProperties properties = new MessageProperties();
    applyCqrsHeaders(properties, messageType, snapshotContextHeaders());

    Message requestMessage = converter.toMessage(payload, properties);
    Message reply = rabbitTemplate.sendAndReceive(exchange, routingKey, requestMessage);

    if (reply == null) {
      return null;
    }

    checkError(reply);

    return converter.fromMessage(reply);
  }

  public Object publishAndReceive(
      String exchange,
      String routingKey,
      Object payload,
      String messageType,
      ParameterizedTypeReference<?> responseType) {
    MessageConverter converter = rabbitTemplate.getMessageConverter();
    MessageProperties properties = new MessageProperties();
    applyCqrsHeaders(properties, messageType, snapshotContextHeaders());

    Message requestMessage = converter.toMessage(payload, properties);
    Message reply = rabbitTemplate.sendAndReceive(exchange, routingKey, requestMessage);

    if (reply == null) {
      return null;
    }

    checkError(reply);

    if (responseType != null && converter instanceof SmartMessageConverter smartConverter) {
      return smartConverter.fromMessage(reply, responseType);
    }
    return converter.fromMessage(reply);
  }

  public void checkError(Message reply) {
    Object errorHeader = reply.getMessageProperties().getHeader(HEADER_ERROR);
    if (Boolean.TRUE.equals(errorHeader)) {
      String errorMessage = new String(reply.getBody());
      throw new RuntimeException("Remote handler error: " + errorMessage);
    }
  }

  private Map<String, String> snapshotContextHeaders() {
    return ContextPropagationMiddleware.headerMap(MessageContext.current(), contextHeaderPrefix);
  }

  private void applyCqrsHeaders(
      MessageProperties properties, String messageType, Map<String, String> contextHeaders) {
    properties.setHeader(HEADER_MESSAGE_TYPE, messageType);
    for (Map.Entry<String, String> entry : contextHeaders.entrySet()) {
      properties.setHeader(entry.getKey(), entry.getValue());
    }
  }
}
