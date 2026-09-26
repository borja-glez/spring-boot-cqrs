package com.borjaglez.cqrs.kafka.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.time.Instant;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.KafkaHeaders;

import com.borjaglez.cqrs.kafka.consumer.UnprocessableRecordException;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.retry.BackoffStrategy;

/**
 * Retry and dead-letter handling of the commands, events and queries listener containers.
 *
 * <p>The error handler is built per container instead of being exposed as a {@link
 * CommonErrorHandler} bean: Spring Boot applies a unique {@code CommonErrorHandler} bean to its
 * {@code @KafkaListener} container factory, and this one publishes to the CQRS dead-letter topics
 * with the CQRS template, which is wrong for the application's own listeners.
 */
final class KafkaErrorHandling {

  /** Name of the {@link CommonErrorHandler} bean used when the application defines several. */
  static final String ERROR_HANDLER_BEAN_NAME = "cqrsKafkaErrorHandler";

  private KafkaErrorHandling() {}

  /**
   * Applies the application's {@link CommonErrorHandler} to {@code container}, or an error handler
   * built from {@code properties} when it has none.
   *
   * @param qualifiedErrorHandler the handler named {@value #ERROR_HANDLER_BEAN_NAME}, if any
   * @param errorHandlers every handler of the application; used when there is a single or a primary
   *     one
   */
  static void configure(
      ConcurrentMessageListenerContainer<String, byte[]> container,
      ObjectProvider<CommonErrorHandler> qualifiedErrorHandler,
      ObjectProvider<CommonErrorHandler> errorHandlers,
      KafkaCqrsProperties.ErrorHandlingProperties properties,
      KafkaOperations<String, byte[]> template,
      String deadLetterTopic) {
    CommonErrorHandler errorHandler = qualifiedErrorHandler.getIfAvailable();
    if (errorHandler == null) {
      errorHandler = errorHandlers.getIfUnique();
    }
    container.setCommonErrorHandler(
        errorHandler != null ? errorHandler : errorHandler(properties, template, deadLetterTopic));
    // Lets the dead-letter headers tell how many deliveries were made.
    container.getContainerProperties().setDeliveryAttemptHeader(true);
  }

  /**
   * An error handler that retries a failed record {@code max-attempts - 1} times with exponential
   * back-off and then, when dead-lettering is enabled, publishes it to {@code deadLetterTopic}.
   * {@link UnprocessableRecordException}s are not retried.
   */
  static DefaultErrorHandler errorHandler(
      KafkaCqrsProperties.ErrorHandlingProperties properties,
      KafkaOperations<String, byte[]> template,
      String deadLetterTopic) {
    if (properties.getMaxAttempts() < 1) {
      throw new IllegalArgumentException(
          "cqrs.kafka.error-handling.max-attempts must be at least 1, but was "
              + properties.getMaxAttempts());
    }
    KafkaCqrsProperties.BackOffProperties backOff = properties.getBackOff();
    BackoffStrategyBackOff backOffPolicy =
        new BackoffStrategyBackOff(
            BackoffStrategy.exponential(
                backOff.getInitialInterval(), backOff.getMultiplier(), backOff.getMaxInterval()),
            properties.getMaxAttempts());
    DefaultErrorHandler errorHandler;
    if (properties.getDeadLetter().isEnabled()) {
      // A negative partition leaves the choice to the partitioner, so the dead-letter topic does
      // not need as many partitions as the source topic.
      DeadLetterPublishingRecoverer recoverer =
          new DeadLetterPublishingRecoverer(
              template, (record, exception) -> new TopicPartition(deadLetterTopic, -1));
      recoverer.addHeadersFunction(KafkaErrorHandling::failureHeaders);
      errorHandler = new DefaultErrorHandler(recoverer, backOffPolicy);
    } else {
      errorHandler = new DefaultErrorHandler(backOffPolicy);
    }
    errorHandler.addNotRetryableExceptions(UnprocessableRecordException.class);
    return errorHandler;
  }

  /** The dead-letter topic of one bus, declared when the application creates its topics. */
  static NewTopic deadLetterTopic(
      KafkaCqrsProperties properties,
      KafkaTopicNamingStrategy namingStrategy,
      String applicationName,
      String logicalName) {
    KafkaCqrsProperties.DeadLetterProperties deadLetter =
        properties.getErrorHandling().getDeadLetter();
    return new NewTopic(
        namingStrategy.deadLetterTopic(applicationName, logicalName),
        deadLetter.getPartitions(),
        deadLetter.getReplicas());
  }

  /**
   * The {@code cqrs.error.*} headers of a dead-lettered record, the same ones the RabbitMQ module
   * sets on dead-lettered messages.
   */
  static Headers failureHeaders(ConsumerRecord<?, ?> record, Exception exception) {
    Throwable failure = handlerFailure(exception);
    Headers headers = new RecordHeaders();
    add(headers, KafkaMessageHeaders.ERROR_TYPE, failure.getClass().getName());
    String message = failure.getMessage();
    if (message != null) {
      add(
          headers,
          KafkaMessageHeaders.ERROR_MESSAGE,
          message.length() > KafkaMessageHeaders.MAX_ERROR_MESSAGE_LENGTH
              ? message.substring(0, KafkaMessageHeaders.MAX_ERROR_MESSAGE_LENGTH)
              : message);
    }
    add(headers, KafkaMessageHeaders.ERROR_ATTEMPTS, String.valueOf(deliveryAttempts(record)));
    add(headers, KafkaMessageHeaders.ERROR_TIMESTAMP, Instant.now().toString());
    return headers;
  }

  /** The exception thrown by the handler, without the wrappers added by the listener container. */
  static Throwable handlerFailure(Throwable error) {
    Throwable current = error;
    while (current instanceof ListenerExecutionFailedException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static int deliveryAttempts(ConsumerRecord<?, ?> record) {
    Header header = record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT);
    return header == null ? 1 : ByteBuffer.wrap(header.value()).getInt();
  }

  private static void add(Headers headers, String name, String value) {
    headers.add(name, value.getBytes(UTF_8));
  }
}
