package com.borjaglez.cqrs.rabbitmq.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.rabbitmq.RemoteHandlerException;
import com.borjaglez.cqrs.rabbitmq.RemoteReplyTimeoutException;

public class RabbitMqPublisher {

  public static final String DEFAULT_CONTEXT_HEADER_PREFIX = "cqrs.context.";

  private static final String HEADER_MESSAGE_TYPE = "cqrs.message.type";
  private static final String HEADER_ERROR = "cqrs.error";
  private static final String HEADER_ERROR_TYPE = "cqrs.error.type";
  private static final String HEADER_NULL_RESULT = "cqrs.result.null";

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
    Message reply = receive(exchange, routingKey, requestMessage);
    if (isNullResult(reply)) {
      return null;
    }
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
    Message reply = receive(exchange, routingKey, requestMessage);
    if (isNullResult(reply)) {
      return null;
    }
    if (responseType != null && converter instanceof SmartMessageConverter smartConverter) {
      return smartConverter.fromMessage(reply, responseType);
    }
    return converter.fromMessage(reply);
  }

  /**
   * Sends the request and waits for the reply. A missing reply is a timeout, never a {@code null}
   * result: handlers answer {@code null} explicitly.
   */
  private Message receive(String exchange, String routingKey, Message requestMessage) {
    Message reply = rabbitTemplate.sendAndReceive(exchange, routingKey, requestMessage);
    if (reply == null) {
      throw new RemoteReplyTimeoutException(exchange, routingKey);
    }
    checkError(reply);
    return reply;
  }

  private static boolean isNullResult(Message reply) {
    return Boolean.TRUE.equals(reply.getMessageProperties().getHeader(HEADER_NULL_RESULT));
  }

  public void checkError(Message reply) {
    Object errorHeader = reply.getMessageProperties().getHeader(HEADER_ERROR);
    if (Boolean.TRUE.equals(errorHeader)) {
      String errorMessage = new String(reply.getBody(), StandardCharsets.UTF_8);
      Object errorType = reply.getMessageProperties().getHeader(HEADER_ERROR_TYPE);
      throw new RemoteHandlerException(
          errorType != null ? errorType.toString() : null, errorMessage);
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
