package com.borjaglez.cqrs.kafka.consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.serialization.MessageSerializer;

abstract class AbstractKafkaConsumer {

  private final MessageSerializer serializer;
  private final String contextHeaderPrefix;

  protected AbstractKafkaConsumer(MessageSerializer serializer, String contextHeaderPrefix) {
    this.serializer = serializer;
    this.contextHeaderPrefix =
        Objects.requireNonNullElse(
            contextHeaderPrefix, KafkaMessagePublisher.DEFAULT_CONTEXT_HEADER_PREFIX);
  }

  /**
   * The class of the payload, when this application has it. Every service reads the shared
   * commands, queries and events topics, so a record may well carry a type that only another
   * service knows: that is not an error, the record is simply not for this application.
   */
  protected Optional<Class<?>> localPayloadClass(ConsumerRecord<String, byte[]> record) {
    String payloadType = header(record, KafkaMessageHeaders.PAYLOAD_TYPE);
    if (payloadType == null) {
      throw new UnprocessableRecordException("Missing Kafka CQRS payload type header");
    }
    try {
      return Optional.of(Class.forName(payloadType));
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  /**
   * Deserializes the payload. A payload that cannot be read as {@code payloadClass} will not become
   * readable on a later attempt, so the failure is reported as an {@link
   * UnprocessableRecordException}, which is not retried.
   */
  protected <T> T deserialize(ConsumerRecord<String, byte[]> record, Class<T> payloadClass) {
    try {
      return serializer.deserialize(record.value(), payloadClass);
    } catch (RuntimeException e) {
      throw new UnprocessableRecordException(
          "Cannot deserialize Kafka CQRS payload of type " + payloadClass.getName(), e);
    }
  }

  protected String header(ConsumerRecord<String, byte[]> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), UTF_8);
  }

  protected MessageContext extractContext(ConsumerRecord<String, byte[]> record) {
    Map<String, String> headers = new LinkedHashMap<>();
    for (Header header : record.headers()) {
      headers.put(header.key(), new String(header.value(), UTF_8));
    }
    return ContextPropagationMiddleware.fromHeaders(headers, contextHeaderPrefix);
  }
}
