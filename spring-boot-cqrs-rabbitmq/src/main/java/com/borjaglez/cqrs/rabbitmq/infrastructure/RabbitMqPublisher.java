package com.borjaglez.cqrs.rabbitmq.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.rabbitmq.PublishNotConfirmedException;
import com.borjaglez.cqrs.rabbitmq.RemoteHandlerException;
import com.borjaglez.cqrs.rabbitmq.RemoteReplyTimeoutException;

public class RabbitMqPublisher {

  public static final String DEFAULT_CONTEXT_HEADER_PREFIX = "cqrs.context.";

  /**
   * Logical name of the message (its routing key), which the consumer uses to read it as its own
   * class whatever class the producer used.
   */
  public static final String HEADER_MESSAGE_NAME = "cqrs.message.name";

  private static final String HEADER_MESSAGE_TYPE = "cqrs.message.type";
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
    rabbitTemplate.convertAndSend(
        exchange, routingKey, message, cqrsHeaders(routingKey, messageType));
  }

  /**
   * Publishes the message and waits up to {@code timeout} for the broker to confirm it. Needs a
   * connection factory with correlated publisher confirms ({@code
   * spring.rabbitmq.publisher-confirm-type=correlated}).
   *
   * @throws PublishNotConfirmedException when the broker rejects the message or does not confirm it
   *     in time
   */
  public void publishConfirmed(
      String exchange, String routingKey, Object message, String messageType, Duration timeout) {
    CorrelationData correlation = new CorrelationData();
    rabbitTemplate.convertAndSend(
        exchange, routingKey, message, cqrsHeaders(routingKey, messageType), correlation);
    CorrelationData.Confirm confirm = awaitConfirm(correlation, exchange, routingKey, timeout);
    if (!confirm.isAck()) {
      throw new PublishNotConfirmedException(
          "Message "
              + routingKey
              + " sent to "
              + exchange
              + " was rejected by the broker: "
              + confirm.getReason());
    }
  }

  private static CorrelationData.Confirm awaitConfirm(
      CorrelationData correlation, String exchange, String routingKey, Duration timeout) {
    String description = "Message " + routingKey + " sent to " + exchange;
    try {
      return correlation.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new PublishNotConfirmedException(
          description + " was not confirmed by the broker within " + timeout, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PublishNotConfirmedException(
          description + " was not confirmed: interrupted while waiting for the broker", e);
    } catch (ExecutionException e) {
      throw new PublishNotConfirmedException(
          description + " was not confirmed by the broker", e.getCause());
    }
  }

  private MessagePostProcessor cqrsHeaders(String routingKey, String messageType) {
    Map<String, String> contextHeaders = snapshotContextHeaders();
    return m -> {
      applyCqrsHeaders(m.getMessageProperties(), routingKey, messageType, contextHeaders);
      return m;
    };
  }

  public Object publishAndReceive(
      String exchange, String routingKey, Object payload, String messageType) {
    MessageConverter converter = rabbitTemplate.getMessageConverter();
    MessageProperties properties = new MessageProperties();
    applyCqrsHeaders(properties, routingKey, messageType, snapshotContextHeaders());

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
    applyCqrsHeaders(properties, routingKey, messageType, snapshotContextHeaders());

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
    Object errorHeader = reply.getMessageProperties().getHeader(RabbitMqErrorHeaders.ERROR);
    if (Boolean.TRUE.equals(errorHeader)) {
      String errorMessage = new String(reply.getBody(), StandardCharsets.UTF_8);
      Object errorType = reply.getMessageProperties().getHeader(RabbitMqErrorHeaders.ERROR_TYPE);
      throw new RemoteHandlerException(
          errorType != null ? errorType.toString() : null, errorMessage);
    }
  }

  private Map<String, String> snapshotContextHeaders() {
    return ContextPropagationMiddleware.headerMap(MessageContext.current(), contextHeaderPrefix);
  }

  private void applyCqrsHeaders(
      MessageProperties properties,
      String routingKey,
      String messageType,
      Map<String, String> contextHeaders) {
    properties.setHeader(HEADER_MESSAGE_TYPE, messageType);
    properties.setHeader(HEADER_MESSAGE_NAME, routingKey);
    for (Map.Entry<String, String> entry : contextHeaders.entrySet()) {
      properties.setHeader(entry.getKey(), entry.getValue());
    }
  }
}
