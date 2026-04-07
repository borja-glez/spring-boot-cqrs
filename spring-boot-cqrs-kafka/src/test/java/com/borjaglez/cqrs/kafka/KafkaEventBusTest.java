package com.borjaglez.cqrs.kafka;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.kafka.fixtures.TestEvent;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;

class KafkaEventBusTest {

  private KafkaMessagePublisher publisher;
  private KafkaTopicNamingStrategy topicNamingStrategy;
  private EventBus fallbackEventBus;
  private KafkaEventBus eventBus;

  @BeforeEach
  void setUp() {
    publisher = mock(KafkaMessagePublisher.class);
    topicNamingStrategy = mock(KafkaTopicNamingStrategy.class);
    fallbackEventBus = mock(EventBus.class);
    eventBus = new KafkaEventBus(publisher, topicNamingStrategy, "events", fallbackEventBus);
    when(topicNamingStrategy.topic("events")).thenReturn("cqrs.events");
  }

  @Test
  void publishShouldUseConfiguredTopic() {
    TestEvent event = new TestEvent("value");

    eventBus.publish(event);

    verify(publisher).publish("cqrs.events", event);
    verifyNoInteractions(fallbackEventBus);
  }

  @Test
  void publishShouldFallbackToLocalBusWhenKafkaFails() {
    TestEvent event = new TestEvent("value");
    org.mockito.Mockito.doThrow(new RuntimeException("down"))
        .when(publisher)
        .publish("cqrs.events", event);

    eventBus.publish(event);

    verify(fallbackEventBus).publish(event);
  }

  @Test
  void publishListShouldPublishEachEvent() {
    TestEvent first = new TestEvent("one");
    TestEvent second = new TestEvent("two");

    eventBus.publish(List.of(first, second));

    verify(publisher).publish("cqrs.events", first);
    verify(publisher).publish("cqrs.events", second);
  }
}
