package com.borjaglez.cqrs.kafka.consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.kafka.KafkaMessagePublisher;
import com.borjaglez.cqrs.kafka.fixtures.TestCommand;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestMode;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.cqrs.middleware.BusMiddleware;

class KafkaCommandConsumerTest {

  private CommandHandlerRegistry registry;
  private MessageSerializer serializer;
  private KafkaMessagePublisher publisher;
  private KafkaCommandConsumer consumer;

  @BeforeEach
  void setUp() {
    registry = mock(CommandHandlerRegistry.class);
    serializer = mock(MessageSerializer.class);
    publisher = mock(KafkaMessagePublisher.class);
    consumer = new KafkaCommandConsumer(registry, Collections.emptyList(), serializer, publisher);
  }

  @Test
  void shouldHandleFireAndForgetCommandWithoutReply() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, null, null);
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);

    consumer.consume(record);

    verify(registry).handle(command);
    verifyNoInteractions(publisher);
  }

  @Test
  void shouldReplyWithHandlerResult() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.REPLY, "reply-topic");
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);
    when(registry.handle(command)).thenReturn("done");

    consumer.consume(record);

    verify(publisher).publishReply("reply-topic", correlationId(record), "done");
  }

  @Test
  void shouldReplyWithAckForWaitMode() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.WAIT, "reply-topic");
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);
    when(registry.handle(command)).thenReturn("ignored");

    consumer.consume(record);

    verify(publisher).publishReply("reply-topic", correlationId(record), "");
  }

  @Test
  void shouldReplyWithErrorForRequestReplyFailures() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.REPLY, "reply-topic");
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);
    when(registry.handle(command)).thenThrow(new RuntimeException("boom"));

    consumer.consume(record);

    verify(publisher)
        .publishErrorReply(
            org.mockito.Mockito.eq("reply-topic"),
            org.mockito.Mockito.eq(correlationId(record)),
            argThat(error -> error.getMessage().equals("boom")));
  }

  @Test
  void shouldRethrowFailuresForFireAndForgetCommands() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, null, null);
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);
    when(registry.handle(command)).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> consumer.consume(record)).hasMessage("boom");
  }

  @Test
  void shouldPublishCheckedFailuresForRequestReplyCommands() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.REPLY, "reply-topic");
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);
    BusMiddleware failingMiddleware =
        (message, chain) -> {
          throw new Exception("boom");
        };
    consumer = new KafkaCommandConsumer(registry, List.of(failingMiddleware), serializer, publisher);

    consumer.consume(record);

    verify(publisher)
        .publishErrorReply(
            org.mockito.Mockito.eq("reply-topic"),
            org.mockito.Mockito.eq(correlationId(record)),
            argThat(error -> error.getCause() != null && error.getCause().getMessage().equals("boom")));
  }

  @Test
  void shouldWrapCheckedFailuresForFireAndForgetCommands() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, null, null);
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);
    BusMiddleware failingMiddleware =
        (message, chain) -> {
          throw new Exception("boom");
        };
    consumer = new KafkaCommandConsumer(registry, List.of(failingMiddleware), serializer, publisher);

    assertThatThrownBy(() -> consumer.consume(record))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(Exception.class)
        .rootCause()
        .hasMessage("boom");
  }

  @Test
  void shouldNotReplyWhenReplyModeIsMissingReplyTopic() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.REPLY, null);
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);

    consumer.consume(record);

    verifyNoInteractions(publisher);
  }

  @Test
  void shouldNotReplyWhenReplyModeIsMissingCorrelationId() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.REPLY, "reply-topic");
    record.headers().remove(KafkaMessageHeaders.CORRELATION_ID);
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);

    consumer.consume(record);

    verifyNoInteractions(publisher);
  }

  @Test
  void shouldNotReplyWhenWaitModeIsMissingReplyTopic() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.WAIT, null);
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);

    consumer.consume(record);

    verifyNoInteractions(publisher);
  }

  @Test
  void shouldNotReplyWhenWaitModeIsMissingCorrelationId() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.WAIT, "reply-topic");
    record.headers().remove(KafkaMessageHeaders.CORRELATION_ID);
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);

    consumer.consume(record);

    verifyNoInteractions(publisher);
  }

  @Test
  void shouldRethrowRuntimeFailuresWhenReplyTopicIsMissing() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.REPLY, null);
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);
    when(registry.handle(command)).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> consumer.consume(record)).hasMessage("boom");
  }

  @Test
  void shouldRethrowRuntimeFailuresWhenCorrelationIdIsMissing() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.REPLY, "reply-topic");
    record.headers().remove(KafkaMessageHeaders.CORRELATION_ID);
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);
    when(registry.handle(command)).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> consumer.consume(record)).hasMessage("boom");
  }

  @Test
  void shouldWrapCheckedFailuresWhenCorrelationIdIsMissing() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.REPLY, "reply-topic");
    record.headers().remove(KafkaMessageHeaders.CORRELATION_ID);
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);
    BusMiddleware failingMiddleware =
        (message, chain) -> {
          throw new Exception("boom");
        };
    consumer = new KafkaCommandConsumer(registry, List.of(failingMiddleware), serializer, publisher);

    assertThatThrownBy(() -> consumer.consume(record))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(Exception.class)
        .rootCause()
        .hasMessage("boom");
  }

  @Test
  void shouldWrapCheckedFailuresWhenReplyTopicIsMissing() {
    TestCommand command = new TestCommand("value");
    ConsumerRecord<String, byte[]> record = recordFor(command, KafkaRequestMode.REPLY, null);
    when(serializer.deserialize(record.value(), TestCommand.class)).thenReturn(command);
    BusMiddleware failingMiddleware =
        (message, chain) -> {
          throw new Exception("boom");
        };
    consumer = new KafkaCommandConsumer(registry, List.of(failingMiddleware), serializer, publisher);

    assertThatThrownBy(() -> consumer.consume(record))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(Exception.class)
        .rootCause()
        .hasMessage("boom");
  }

  @Test
  void shouldFailWhenPayloadTypeHeaderIsMissing() {
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>("cqrs.commands", 0, 0L, "key", "payload".getBytes(UTF_8));

    assertThatThrownBy(() -> consumer.consume(record))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Missing Kafka CQRS payload type header");
  }

  @Test
  void shouldFailWhenPayloadTypeHeaderCannotBeResolved() {
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>("cqrs.commands", 0, 0L, "key", "payload".getBytes(UTF_8));
    record.headers().add(
        new RecordHeader(KafkaMessageHeaders.PAYLOAD_TYPE, "com.example.Missing".getBytes(UTF_8)));

    assertThatThrownBy(() -> consumer.consume(record))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to resolve Kafka CQRS payload type com.example.Missing");
  }

  private ConsumerRecord<String, byte[]> recordFor(
      TestCommand command, KafkaRequestMode requestMode, String replyTopic) {
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>("cqrs.commands", 0, 0L, "key", "payload".getBytes(UTF_8));
    record.headers()
        .add(new RecordHeader(KafkaMessageHeaders.PAYLOAD_TYPE, TestCommand.class.getName().getBytes(UTF_8)))
        .add(new RecordHeader(KafkaMessageHeaders.CORRELATION_ID, "corr-1".getBytes(UTF_8)));
    if (requestMode != null) {
      record.headers().add(new RecordHeader(KafkaMessageHeaders.REQUEST_MODE, requestMode.name().getBytes(UTF_8)));
    }
    if (replyTopic != null) {
      record.headers().add(new RecordHeader(KafkaMessageHeaders.REPLY_TOPIC, replyTopic.getBytes(UTF_8)));
    }
    return record;
  }

  private String correlationId(ConsumerRecord<String, byte[]> record) {
    return new String(record.headers().lastHeader(KafkaMessageHeaders.CORRELATION_ID).value(), UTF_8);
  }
}
