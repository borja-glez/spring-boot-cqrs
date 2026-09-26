package com.borjaglez.cqrs.kafka.infrastructure;

public final class KafkaMessageHeaders {

  public static final String MESSAGE_KIND = "cqrs.message.kind";
  public static final String MESSAGE_NAME = "cqrs.message.name";

  /**
   * The key declared by a command or event implementing {@link com.borjaglez.cqrs.KeyedMessage};
   * absent when the message declares no key or a blank one.
   */
  public static final String MESSAGE_KEY = "cqrs.message.key";

  public static final String PAYLOAD_TYPE = "cqrs.payload.type";
  public static final String CORRELATION_ID = "cqrs.correlation.id";
  public static final String REPLY_TOPIC = "cqrs.reply.topic";
  public static final String REQUEST_MODE = "cqrs.request.mode";

  /** Marks a reply as an error reply. */
  public static final String ERROR = "cqrs.error";

  /**
   * Class name of the exception thrown by the handler, on error replies and on dead-lettered
   * records.
   */
  public static final String ERROR_TYPE = "cqrs.error.type";

  /**
   * Message of the exception thrown by the handler, truncated to {@link #MAX_ERROR_MESSAGE_LENGTH}
   * characters. Absent when the exception has no message. Only set on dead-lettered records.
   */
  public static final String ERROR_MESSAGE = "cqrs.error.message";

  /** Number of deliveries made before the record was dead-lettered, as a decimal string. */
  public static final String ERROR_ATTEMPTS = "cqrs.error.attempts";

  /** ISO-8601 instant at which the record was dead-lettered. */
  public static final String ERROR_TIMESTAMP = "cqrs.error.timestamp";

  /** Maximum length of the {@link #ERROR_MESSAGE} header. */
  public static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

  private KafkaMessageHeaders() {}
}
