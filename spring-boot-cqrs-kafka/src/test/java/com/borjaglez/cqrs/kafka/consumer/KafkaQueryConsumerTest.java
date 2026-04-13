package com.borjaglez.cqrs.kafka.consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.fixtures.TestQuery;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.serialization.MessageSerializer;

class KafkaQueryConsumerTest {

  private QueryHandlerRegistry registry;
  private MessageSerializer serializer;
  private KafkaMessagePublisher publisher;
  private KafkaQueryConsumer consumer;

  @BeforeEach
  void setUp() {
    registry = mock(QueryHandlerRegistry.class);
    serializer = mock(MessageSerializer.class);
    publisher = mock(KafkaMessagePublisher.class);
    consumer = new KafkaQueryConsumer(registry, Collections.emptyList(), serializer, publisher);
  }

  @Test
  void shouldReplyWithQueryResult() {
    TestQuery query = new TestQuery("value");
    ConsumerRecord<String, byte[]> record = recordFor();
    when(serializer.deserialize(record.value(), TestQuery.class)).thenReturn(query);
    when(registry.handle(query)).thenReturn("done");

    consumer.consume(record);

    verify(publisher).publishReply("reply-topic", "corr-1", "done");
  }

  @Test
  void shouldReplyWithErrorWhenQueryFails() {
    TestQuery query = new TestQuery("value");
    ConsumerRecord<String, byte[]> record = recordFor();
    when(serializer.deserialize(record.value(), TestQuery.class)).thenReturn(query);
    when(registry.handle(query)).thenThrow(new RuntimeException("boom"));

    consumer.consume(record);

    verify(publisher)
        .publishErrorReply(
            org.mockito.Mockito.eq("reply-topic"),
            org.mockito.Mockito.eq("corr-1"),
            argThat(error -> error.getMessage().equals("boom")));
  }

  @Test
  void shouldReplyWithWrappedCheckedErrorWhenMiddlewareFails() {
    TestQuery query = new TestQuery("value");
    ConsumerRecord<String, byte[]> record = recordFor();
    when(serializer.deserialize(record.value(), TestQuery.class)).thenReturn(query);
    BusMiddleware failingMiddleware =
        (message, chain) -> {
          throw new Exception("boom");
        };
    consumer = new KafkaQueryConsumer(registry, List.of(failingMiddleware), serializer, publisher);

    consumer.consume(record);

    verify(publisher)
        .publishErrorReply(
            org.mockito.Mockito.eq("reply-topic"),
            org.mockito.Mockito.eq("corr-1"),
            argThat(
                error -> error.getCause() != null && error.getCause().getMessage().equals("boom")));
  }

  @Test
  void shouldExposeContextFromHeaders() {
    java.util.concurrent.atomic.AtomicReference<String> observed =
        new java.util.concurrent.atomic.AtomicReference<>();
    BusMiddleware middleware =
        (msg, chain) -> {
          observed.set(MessageContext.current().correlationId());
          return chain.proceed(msg);
        };
    consumer =
        new KafkaQueryConsumer(
            registry, List.of(middleware), serializer, publisher, "cqrs.context.");

    TestQuery query = new TestQuery("value");
    ConsumerRecord<String, byte[]> record = recordFor();
    record.headers().add(new RecordHeader("cqrs.context.correlationId", "cid-q".getBytes(UTF_8)));
    when(serializer.deserialize(record.value(), TestQuery.class)).thenReturn(query);
    when(registry.handle(query)).thenReturn("r");

    consumer.consume(record);

    org.assertj.core.api.Assertions.assertThat(observed.get()).isEqualTo("cid-q");
    verify(publisher).publishReply("reply-topic", "corr-1", "r");
  }

  @Test
  void shouldFailWhenPayloadTypeHeaderIsMissing() {
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>("cqrs.queries", 0, 0L, "key", "payload".getBytes(UTF_8));

    assertThatThrownBy(() -> consumer.consume(record))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Missing Kafka CQRS payload type header");
  }

  private ConsumerRecord<String, byte[]> recordFor() {
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>("cqrs.queries", 0, 0L, "key", "payload".getBytes(UTF_8));
    record
        .headers()
        .add(
            new RecordHeader(
                KafkaMessageHeaders.PAYLOAD_TYPE, TestQuery.class.getName().getBytes(UTF_8)))
        .add(new RecordHeader(KafkaMessageHeaders.REPLY_TOPIC, "reply-topic".getBytes(UTF_8)))
        .add(new RecordHeader(KafkaMessageHeaders.CORRELATION_ID, "corr-1".getBytes(UTF_8)));
    return record;
  }
}
