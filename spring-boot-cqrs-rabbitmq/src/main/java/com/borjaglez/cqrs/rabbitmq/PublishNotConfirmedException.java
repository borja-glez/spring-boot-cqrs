package com.borjaglez.cqrs.rabbitmq;

import org.springframework.amqp.AmqpException;

/**
 * The broker did not confirm a message published with publisher confirms enabled: it rejected it (a
 * negative acknowledgement) or the confirmation did not arrive within the configured timeout.
 *
 * <p>After a timeout the broker may still have accepted the message, so a caller that retries can
 * publish it twice: consumers of events published this way should be idempotent.
 */
public class PublishNotConfirmedException extends AmqpException {

  public PublishNotConfirmedException(String message) {
    super(message);
  }

  public PublishNotConfirmedException(String message, Throwable cause) {
    super(message, cause);
  }
}
