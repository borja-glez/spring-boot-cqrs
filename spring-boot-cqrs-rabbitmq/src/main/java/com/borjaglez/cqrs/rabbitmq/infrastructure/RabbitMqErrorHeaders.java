package com.borjaglez.cqrs.rabbitmq.infrastructure;

import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

/**
 * Names of the headers that describe a failure: the error replies of request-reply commands and
 * queries, and the messages sent to a dead-letter queue.
 */
public final class RabbitMqErrorHeaders {

  /** Marks a reply as an error reply. */
  public static final String ERROR = "cqrs.error";

  /** Class name of the exception thrown by the handler. */
  public static final String ERROR_TYPE = "cqrs.error.type";

  /**
   * Message of the exception thrown by the handler, truncated to {@link #MAX_ERROR_MESSAGE_LENGTH}
   * characters. Absent when the exception has no message. Only set on dead-lettered messages.
   */
  public static final String ERROR_MESSAGE = "cqrs.error.message";

  /** Number of deliveries made before the message was dead-lettered. */
  public static final String ERROR_ATTEMPTS = "cqrs.error.attempts";

  /** ISO-8601 instant at which the message was dead-lettered. */
  public static final String ERROR_TIMESTAMP = "cqrs.error.timestamp";

  /** Maximum length of the {@link #ERROR_MESSAGE} header. */
  public static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

  private RabbitMqErrorHeaders() {}

  /**
   * The exception thrown by the handler, without the wrappers added by the Spring AMQP listener
   * adapter.
   *
   * @param error the exception caught while consuming a message
   * @return the handler's exception
   */
  public static Throwable handlerFailure(Throwable error) {
    Throwable current = error;
    while (current instanceof ListenerExecutionFailedException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
