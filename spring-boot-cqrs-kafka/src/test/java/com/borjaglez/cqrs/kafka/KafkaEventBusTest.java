package com.borjaglez.cqrs.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.kafka.fixtures.TestEvent;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;

class KafkaEventBusTest {

  private KafkaMessagePublisher publisher;
  private KafkaTopicNamingStrategy topicNamingStrategy;
  private KafkaEventBus eventBus;

  @BeforeEach
  void setUp() {
    publisher = mock(KafkaMessagePublisher.class);
    topicNamingStrategy = mock(KafkaTopicNamingStrategy.class);
    eventBus = new KafkaEventBus(publisher, topicNamingStrategy, "events");
    when(topicNamingStrategy.topic("events")).thenReturn("cqrs.events");
  }

  @Test
  void publishShouldUseConfiguredTopic() {
    TestEvent event = new TestEvent("value");

    eventBus.publish(event);

    verify(publisher).publish("cqrs.events", event);
  }

  @Test
  void publishShouldPropagateTheBrokerFailureUnchanged() {
    TestEvent event = new TestEvent("value");
    IllegalStateException failure = new IllegalStateException("broker down");
    doThrow(failure).when(publisher).publish("cqrs.events", event);

    assertThatThrownBy(() -> eventBus.publish(event))
        .isSameAs(failure)
        .hasMessageContaining("broker down");
  }

  @Test
  void publishListShouldPublishEachEventInOrder() {
    TestEvent first = new TestEvent("one");
    TestEvent second = new TestEvent("two");

    eventBus.publish(List.of(first, second));

    var order = inOrder(publisher);
    order.verify(publisher).publish("cqrs.events", first);
    order.verify(publisher).publish("cqrs.events", second);
  }

  @Test
  void publishListShouldStopAtTheFirstFailureAndPropagateIt() {
    TestEvent first = new TestEvent("one");
    TestEvent failing = new TestEvent("two");
    TestEvent notSent = new TestEvent("three");
    IllegalStateException failure = new IllegalStateException("broker down");
    doThrow(failure).when(publisher).publish("cqrs.events", failing);

    assertThatThrownBy(() -> eventBus.publish(List.of(first, failing, notSent))).isSameAs(failure);

    verify(publisher).publish("cqrs.events", first);
    verify(publisher).publish("cqrs.events", failing);
    verify(publisher, never()).publish("cqrs.events", notSent);
  }
}
