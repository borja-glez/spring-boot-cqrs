package com.borjaglez.cqrs.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;
import java.util.concurrent.CompletionException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageKind;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaPartitionKeyStrategy;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.serialization.MessageSerializer;

public class KafkaMessagePublisher {

  public static final String DEFAULT_CONTEXT_HEADER_PREFIX = "cqrs.context.";

  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final MessageSerializer serializer;
  private final KafkaPartitionKeyStrategy partitionKeyStrategy;
  private final MessageNamingStrategy messageNamingStrategy;
  private final String contextHeaderPrefix;

  public KafkaMessagePublisher(
      KafkaTemplate<String, byte[]> kafkaTemplate,
      MessageSerializer serializer,
      KafkaPartitionKeyStrategy partitionKeyStrategy,
      MessageNamingStrategy messageNamingStrategy) {
    this(
        kafkaTemplate,
        serializer,
        partitionKeyStrategy,
        messageNamingStrategy,
        DEFAULT_CONTEXT_HEADER_PREFIX);
  }

  public KafkaMessagePublisher(
      KafkaTemplate<String, byte[]> kafkaTemplate,
      MessageSerializer serializer,
      KafkaPartitionKeyStrategy partitionKeyStrategy,
      MessageNamingStrategy messageNamingStrategy,
      String contextHeaderPrefix) {
    this.kafkaTemplate = kafkaTemplate;
    this.serializer = serializer;
    this.partitionKeyStrategy = partitionKeyStrategy;
    this.messageNamingStrategy = messageNamingStrategy;
    this.contextHeaderPrefix =
        contextHeaderPrefix == null ? DEFAULT_CONTEXT_HEADER_PREFIX : contextHeaderPrefix;
  }

  public void publish(String topic, Object message) {
    KafkaMessageKind messageKind = inferKind(message);
    ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(
            topic,
            partitionKeyStrategy.partitionKey(messageKind, message),
            serializer.serialize(message));
    record
        .headers()
        .add(
            new RecordHeader(KafkaMessageHeaders.MESSAGE_KIND, messageKind.name().getBytes(UTF_8)));
    record
        .headers()
        .add(
            new RecordHeader(
                KafkaMessageHeaders.MESSAGE_NAME,
                resolveMessageName(messageKind, message).getBytes(UTF_8)));
    record
        .headers()
        .add(
            new RecordHeader(
                KafkaMessageHeaders.PAYLOAD_TYPE, message.getClass().getName().getBytes(UTF_8)));
    addContextHeaders(record);
    try {
      kafkaTemplate.send(record).join();
    } catch (CompletionException e) {
      throw unwrap(e);
    }
  }

  public void publishReply(String topic, String correlationId, Object payload) {
    Object safePayload = payload == null ? "" : payload;
    ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(topic, correlationId, serializer.serialize(safePayload));
    record
        .headers()
        .add(new RecordHeader(KafkaMessageHeaders.CORRELATION_ID, correlationId.getBytes(UTF_8)));
    record
        .headers()
        .add(
            new RecordHeader(
                KafkaMessageHeaders.PAYLOAD_TYPE,
                safePayload.getClass().getName().getBytes(UTF_8)));
    kafkaTemplate.send(record).join();
  }

  public void publishErrorReply(String topic, String correlationId, RuntimeException error) {
    ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(topic, correlationId, error.getMessage().getBytes(UTF_8));
    record
        .headers()
        .add(new RecordHeader(KafkaMessageHeaders.CORRELATION_ID, correlationId.getBytes(UTF_8)));
    record.headers().add(new RecordHeader(KafkaMessageHeaders.ERROR, "true".getBytes(UTF_8)));
    record
        .headers()
        .add(
            new RecordHeader(
                KafkaMessageHeaders.PAYLOAD_TYPE, String.class.getName().getBytes(UTF_8)));
    kafkaTemplate.send(record).join();
  }

  private void addContextHeaders(ProducerRecord<String, byte[]> record) {
    Map<String, String> headers =
        ContextPropagationMiddleware.headerMap(MessageContext.current(), contextHeaderPrefix);
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      record.headers().add(new RecordHeader(entry.getKey(), entry.getValue().getBytes(UTF_8)));
    }
  }

  private KafkaMessageKind inferKind(Object message) {
    if (message instanceof Command) {
      return KafkaMessageKind.COMMAND;
    }
    if (message instanceof Event) {
      return KafkaMessageKind.EVENT;
    }
    if (message instanceof Query) {
      return KafkaMessageKind.QUERY;
    }
    throw new IllegalArgumentException(
        "Unsupported CQRS message type: " + message.getClass().getName());
  }

  private String resolveMessageName(KafkaMessageKind messageKind, Object message) {
    return switch (messageKind) {
      case COMMAND -> messageNamingStrategy.commandName(((Command) message).getClass());
      case EVENT -> messageNamingStrategy.eventName(((Event) message).getClass());
      case QUERY -> messageNamingStrategy.queryName(((Query) message).getClass());
    };
  }

  private RuntimeException unwrap(CompletionException exception) {
    if (exception.getCause() instanceof RuntimeException runtimeException) {
      return runtimeException;
    }
    return new RuntimeException(exception.getCause());
  }
}
