package com.borjaglez.cqrs.kafka.consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.kafka.fixtures.TestEvent;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageHeaders;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.serialization.MessageSerializer;

class KafkaEventConsumerTest {

  private EventHandlerRegistry registry;
  private MessageSerializer serializer;
  private KafkaEventConsumer consumer;

  @BeforeEach
  void setUp() {
    registry = mock(EventHandlerRegistry.class);
    serializer = mock(MessageSerializer.class);
    consumer = new KafkaEventConsumer(registry, Collections.emptyList(), serializer);
  }

  @Test
  void shouldHandleEvent() {
    TestEvent event = new TestEvent("value");
    ConsumerRecord<String, byte[]> record = recordFor();
    when(serializer.deserialize(record.value(), TestEvent.class)).thenReturn(event);

    consumer.consume(record);

    verify(registry).handle(event);
  }

  @Test
  void shouldRethrowFailures() {
    TestEvent event = new TestEvent("value");
    ConsumerRecord<String, byte[]> record = recordFor();
    when(serializer.deserialize(record.value(), TestEvent.class)).thenReturn(event);
    org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(registry).handle(event);

    assertThatThrownBy(() -> consumer.consume(record)).hasMessage("boom");
  }

  @Test
  void shouldWrapCheckedFailures() {
    TestEvent event = new TestEvent("value");
    ConsumerRecord<String, byte[]> record = recordFor();
    when(serializer.deserialize(record.value(), TestEvent.class)).thenReturn(event);
    BusMiddleware failingMiddleware =
        (message, chain) -> {
          throw new Exception("boom");
        };
    consumer = new KafkaEventConsumer(registry, List.of(failingMiddleware), serializer);

    assertThatThrownBy(() -> consumer.consume(record))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(Exception.class)
        .rootCause()
        .hasMessage("boom");
  }

  private ConsumerRecord<String, byte[]> recordFor() {
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>("cqrs.events", 0, 0L, "key", "payload".getBytes(UTF_8));
    record
        .headers()
        .add(
            new RecordHeader(
                KafkaMessageHeaders.PAYLOAD_TYPE, TestEvent.class.getName().getBytes(UTF_8)));
    return record;
  }
}
