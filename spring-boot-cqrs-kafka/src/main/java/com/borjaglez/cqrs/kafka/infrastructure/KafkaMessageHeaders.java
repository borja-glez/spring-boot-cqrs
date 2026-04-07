package com.borjaglez.cqrs.kafka.infrastructure;

public final class KafkaMessageHeaders {

  public static final String MESSAGE_KIND = "cqrs.message.kind";
  public static final String MESSAGE_NAME = "cqrs.message.name";
  public static final String PAYLOAD_TYPE = "cqrs.payload.type";
  public static final String CORRELATION_ID = "cqrs.correlation.id";
  public static final String REPLY_TOPIC = "cqrs.reply.topic";
  public static final String REQUEST_MODE = "cqrs.request.mode";
  public static final String ERROR = "cqrs.error";

  private KafkaMessageHeaders() {}
}
