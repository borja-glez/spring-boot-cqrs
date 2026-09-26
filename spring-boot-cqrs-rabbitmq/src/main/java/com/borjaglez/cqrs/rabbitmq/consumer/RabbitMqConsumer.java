package com.borjaglez.cqrs.rabbitmq.consumer;

import java.time.Instant;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqErrorHeaders;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

/**
 * Base class of the RabbitMQ consumers. It holds the retry and dead-letter logic applied when an
 * asynchronous message fails.
 *
 * <p>A failed message is re-sent to the retry exchange with the application name as routing key, so
 * that it only reaches the retry queue of the application whose handler failed. When the retry TTL
 * expires the retry queue dead-letters it back to that application's main queue. Once the
 * configured number of attempts is exhausted, the message is sent to the dead-letter exchange, also
 * with the application name as routing key.
 *
 * <p>A dead-lettered message keeps its body and headers and also records why it failed, in the
 * headers named in {@link RabbitMqErrorHeaders}: the exception type and message, the number of
 * deliveries made and the instant it was dead-lettered.
 */
public abstract class RabbitMqConsumer {

  /** Default total number of deliveries of a message, including the first one. */
  public static final int DEFAULT_MAX_ATTEMPTS = 3;

  private static final String HEADER_REDELIVERY_COUNT = "cqrs.redelivery.count";

  private final RabbitTemplate rabbitTemplate;
  private final RabbitMqNamingStrategy namingStrategy;
  private final int maxAttempts;

  protected RabbitMqConsumer(RabbitTemplate rabbitTemplate, RabbitMqNamingStrategy namingStrategy) {
    this(rabbitTemplate, namingStrategy, DEFAULT_MAX_ATTEMPTS);
  }

  /**
   * Creates a consumer.
   *
   * @param rabbitTemplate template used to re-send failed messages
   * @param namingStrategy naming of the retry and dead-letter exchanges
   * @param maxAttempts total number of deliveries of a message, including the first one; must be at
   *     least 1 ({@code 1} sends a failed message straight to the dead-letter exchange)
   */
  protected RabbitMqConsumer(
      RabbitTemplate rabbitTemplate, RabbitMqNamingStrategy namingStrategy, int maxAttempts) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException(
          "cqrs.rabbitmq.retry.max-attempts must be at least 1, but was " + maxAttempts);
    }
    this.rabbitTemplate = rabbitTemplate;
    this.namingStrategy = namingStrategy;
    this.maxAttempts = maxAttempts;
  }

  /**
   * Retries a failed message or, once its attempts are exhausted, sends it to the dead-letter
   * exchange without recording the failure cause.
   *
   * @param message the message that failed
   * @param exchangeName logical name of the exchange the message was consumed from
   * @param appName name of the application whose handler failed
   */
  protected void handleConsumptionError(Message message, String exchangeName, String appName) {
    handleConsumptionError(message, exchangeName, appName, null);
  }

  /**
   * Retries a failed message or, once its attempts are exhausted, sends it to the dead-letter
   * exchange with the failure recorded in the {@link RabbitMqErrorHeaders} headers.
   *
   * @param message the message that failed
   * @param exchangeName logical name of the exchange the message was consumed from
   * @param appName name of the application whose handler failed
   * @param failure the exception thrown while consuming the message, or {@code null} if unknown
   */
  protected void handleConsumptionError(
      Message message, String exchangeName, String appName, Throwable failure) {
    int redeliveryCount = getRedeliveryCount(message);

    if (redeliveryCount < getMaxRetries()) {
      redeliveryCount++;
      message.getMessageProperties().setHeader(HEADER_REDELIVERY_COUNT, redeliveryCount);

      String retryExchange = namingStrategy.exchangeRetry(exchangeName);
      rabbitTemplate.send(retryExchange, appName, message);
    } else {
      recordFailure(message.getMessageProperties(), redeliveryCount + 1, failure);
      String deadLetterExchange = namingStrategy.exchangeDeadLetter(exchangeName);
      rabbitTemplate.send(deadLetterExchange, appName, message);
    }
  }

  /**
   * Total number of deliveries of a message, including the first one.
   *
   * @return the configured maximum number of attempts
   */
  protected int getMaxAttempts() {
    return maxAttempts;
  }

  /**
   * Number of retries after the first delivery.
   *
   * @return {@code getMaxAttempts() - 1}
   */
  protected int getMaxRetries() {
    return getMaxAttempts() - 1;
  }

  private static void recordFailure(MessageProperties properties, int attempts, Throwable failure) {
    if (failure != null) {
      Throwable cause = RabbitMqErrorHeaders.handlerFailure(failure);
      properties.setHeader(RabbitMqErrorHeaders.ERROR_TYPE, cause.getClass().getName());
      String errorMessage = cause.getMessage();
      if (errorMessage != null) {
        properties.setHeader(RabbitMqErrorHeaders.ERROR_MESSAGE, truncate(errorMessage));
      }
    }
    properties.setHeader(RabbitMqErrorHeaders.ERROR_ATTEMPTS, attempts);
    properties.setHeader(RabbitMqErrorHeaders.ERROR_TIMESTAMP, Instant.now().toString());
  }

  private static String truncate(String value) {
    return value.length() > RabbitMqErrorHeaders.MAX_ERROR_MESSAGE_LENGTH
        ? value.substring(0, RabbitMqErrorHeaders.MAX_ERROR_MESSAGE_LENGTH)
        : value;
  }

  private int getRedeliveryCount(Message message) {
    Object header = message.getMessageProperties().getHeader(HEADER_REDELIVERY_COUNT);
    if (header instanceof Integer count) {
      return count;
    }
    return 0;
  }
}
