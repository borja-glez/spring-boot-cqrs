package com.borjaglez.cqrs.kafka.consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.serialization.MessageSerializer;

abstract class AbstractKafkaConsumer {

  private final MessageSerializer serializer;
  private final String contextHeaderPrefix;

  protected AbstractKafkaConsumer(MessageSerializer serializer, String contextHeaderPrefix) {
    this.serializer = serializer;
    this.contextHeaderPrefix = contextHeaderPrefix;
  }

  protected <T> T deserialize(ConsumerRecord<String, byte[]> record) {
    String payloadType = header(record, KafkaMessageHeaders.PAYLOAD_TYPE);
    if (payloadType == null) {
      throw new IllegalStateException("Missing Kafka CQRS payload type header");
    }
    try {
      @SuppressWarnings("unchecked")
      Class<T> payloadClass = (Class<T>) Class.forName(payloadType);
      return serializer.deserialize(record.value(), payloadClass);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Unable to resolve Kafka CQRS payload type " + payloadType, e);
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
