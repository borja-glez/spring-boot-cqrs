package com.borjaglez.cqrs.kafka.consumer;

/**
 * A record that can never be processed, whatever the number of attempts: it has no payload type
 * header, or its payload cannot be deserialized into that type. The Kafka error handling does not
 * retry it and sends it straight to the dead-letter topic.
 *
 * <p>It extends {@link IllegalStateException}, the exception thrown for these records before it
 * existed.
 */
public class UnprocessableRecordException extends IllegalStateException {

  public UnprocessableRecordException(String message) {
    super(message);
  }

  public UnprocessableRecordException(String message, Throwable cause) {
    super(message, cause);
  }
}
