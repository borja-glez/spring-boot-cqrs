package com.borjaglez.cqrs.kafka.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.serialization.MessageSerializer;

public class KafkaRequestReplyClient {

  private final KafkaTemplate<String, byte[]> kafkaTemplate;
  private final MessageSerializer serializer;
  private final KafkaPartitionKeyStrategy partitionKeyStrategy;
  private final MessageNamingStrategy messageNamingStrategy;
  private final String replyTopic;
  private final Duration timeout;
  private final ConcurrentHashMap<String, CompletableFuture<ConsumerRecord<String, byte[]>>> replies =
      new ConcurrentHashMap<>();

  public KafkaRequestReplyClient(
      KafkaTemplate<String, byte[]> kafkaTemplate,
      MessageSerializer serializer,
      KafkaPartitionKeyStrategy partitionKeyStrategy,
      MessageNamingStrategy messageNamingStrategy,
      String replyTopic,
      Duration timeout) {
    this.kafkaTemplate = kafkaTemplate;
    this.serializer = serializer;
    this.partitionKeyStrategy = partitionKeyStrategy;
    this.messageNamingStrategy = messageNamingStrategy;
    this.replyTopic = replyTopic;
    this.timeout = timeout;
  }

  public <R> R sendAndReceive(
      String messageName,
      String topic,
      Object payload,
      ParameterizedTypeReference<R> responseType,
      KafkaRequestMode requestMode)
      throws Exception {
    KafkaMessageKind messageKind = inferKind(payload);
    String resolvedMessageName =
        (messageName == null || messageName.isBlank())
            ? resolveMessageName(messageKind, payload)
            : messageName;
    String correlationId = UUID.randomUUID().toString();

    ProducerRecord<String, byte[]> record =
        new ProducerRecord<>(
            topic, partitionKeyStrategy.partitionKey(messageKind, payload), serializer.serialize(payload));
    record.headers().add(new RecordHeader(KafkaMessageHeaders.MESSAGE_KIND, messageKind.name().getBytes(UTF_8)));
    record.headers().add(
        new RecordHeader(KafkaMessageHeaders.MESSAGE_NAME, resolvedMessageName.getBytes(UTF_8)));
    record.headers().add(
        new RecordHeader(KafkaMessageHeaders.PAYLOAD_TYPE, payload.getClass().getName().getBytes(UTF_8)));
    record.headers().add(
        new RecordHeader(KafkaMessageHeaders.CORRELATION_ID, correlationId.getBytes(UTF_8)));
    record.headers().add(new RecordHeader(KafkaMessageHeaders.REPLY_TOPIC, replyTopic.getBytes(UTF_8)));
    record.headers().add(
        new RecordHeader(KafkaMessageHeaders.REQUEST_MODE, requestMode.name().getBytes(UTF_8)));

    CompletableFuture<ConsumerRecord<String, byte[]>> future = new CompletableFuture<>();
    replies.put(correlationId, future);
    kafkaTemplate.send(record).join();

    try {
      ConsumerRecord<String, byte[]> response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      return deserializeResponse(response, responseType);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof Exception exception) {
        throw exception;
      }
      throw new RuntimeException(e.getCause());
    } catch (TimeoutException e) {
      throw new RuntimeException("Timed out waiting for Kafka reply for " + resolvedMessageName, e);
    } finally {
      replies.remove(correlationId);
    }
  }

  public void handleReply(ConsumerRecord<String, byte[]> reply) {
    String correlationId = header(reply, KafkaMessageHeaders.CORRELATION_ID);
    if (correlationId == null) {
      return;
    }
    CompletableFuture<ConsumerRecord<String, byte[]>> future = replies.get(correlationId);
    if (future != null) {
      future.complete(reply);
    }
  }

  @SuppressWarnings("unchecked")
  private <R> R deserializeResponse(
      ConsumerRecord<String, byte[]> reply, ParameterizedTypeReference<R> responseType) {
    if (isError(reply)) {
      throw new RuntimeException("Remote handler error: " + new String(reply.value(), UTF_8));
    }
    if (reply.value() == null || reply.value().length == 0) {
      return null;
    }
    if (responseType != null) {
      return serializer.deserialize(reply.value(), responseType);
    }
    Class<?> targetType = resolveResponseClass(reply);
    return (R) serializer.deserialize(reply.value(), targetType);
  }

  private boolean isError(ConsumerRecord<String, byte[]> reply) {
    return "true".equalsIgnoreCase(header(reply, KafkaMessageHeaders.ERROR));
  }

  private Class<?> resolveResponseClass(ConsumerRecord<String, byte[]> reply) {
    String payloadType = header(reply, KafkaMessageHeaders.PAYLOAD_TYPE);
    if (payloadType == null) {
      return String.class;
    }
    try {
      return Class.forName(payloadType);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Unable to resolve Kafka reply payload type: " + payloadType, e);
    }
  }

  private String header(ConsumerRecord<String, byte[]> record, String name) {
    Header header = record.headers().lastHeader(name);
    return header == null ? null : new String(header.value(), UTF_8);
  }

  private KafkaMessageKind inferKind(Object payload) {
    if (payload instanceof Command) {
      return KafkaMessageKind.COMMAND;
    }
    if (payload instanceof Event) {
      return KafkaMessageKind.EVENT;
    }
    if (payload instanceof Query) {
      return KafkaMessageKind.QUERY;
    }
    throw new IllegalArgumentException("Unsupported CQRS message type: " + payload.getClass().getName());
  }

  private String resolveMessageName(KafkaMessageKind messageKind, Object payload) {
    return switch (messageKind) {
      case COMMAND -> messageNamingStrategy.commandName(((Command) payload).getClass());
      case EVENT -> messageNamingStrategy.eventName(((Event) payload).getClass());
      case QUERY -> messageNamingStrategy.queryName(((Query) payload).getClass());
    };
  }
}
