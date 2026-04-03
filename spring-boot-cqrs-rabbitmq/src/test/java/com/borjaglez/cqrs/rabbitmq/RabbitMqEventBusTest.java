package com.borjaglez.cqrs.rabbitmq;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEvent;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

class RabbitMqEventBusTest {

  private RabbitMqPublisher publisher;
  private RabbitMqNamingStrategy rabbitNaming;
  private MessageNamingStrategy messageNaming;
  private EventBus fallbackEventBus;
  private RabbitMqEventBus eventBus;

  @BeforeEach
  void setUp() {
    publisher = mock(RabbitMqPublisher.class);
    rabbitNaming = mock(RabbitMqNamingStrategy.class);
    messageNaming = mock(MessageNamingStrategy.class);
    fallbackEventBus = mock(EventBus.class);
    eventBus =
        new RabbitMqEventBus(publisher, rabbitNaming, messageNaming, "events", fallbackEventBus);
  }

  @Test
  void publishShouldSendToRabbitMq() {
    TestEvent event = new TestEvent("test-data");
    when(rabbitNaming.exchange("events")).thenReturn("cqrs.events");
    when(messageNaming.eventName(TestEvent.class)).thenReturn("test.order.created");

    eventBus.publish(event);

    verify(publisher).publish("cqrs.events", "test.order.created", event, "event");
    verify(fallbackEventBus, never()).publish(any(Event.class));
  }

  @Test
  void publishShouldFallbackToLocalBusOnAmqpException() {
    TestEvent event = new TestEvent("test-data");
    when(rabbitNaming.exchange("events")).thenReturn("cqrs.events");
    when(messageNaming.eventName(TestEvent.class)).thenReturn("test.order.created");
    doThrow(new AmqpException("connection failed"))
        .when(publisher)
        .publish(any(), any(), any(), any());

    eventBus.publish(event);

    verify(fallbackEventBus).publish(event);
  }

  @Test
  void publishListShouldDelegateToSinglePublish() {
    TestEvent event1 = new TestEvent("data1");
    TestEvent event2 = new TestEvent("data2");
    when(rabbitNaming.exchange("events")).thenReturn("cqrs.events");
    when(messageNaming.eventName(TestEvent.class)).thenReturn("test.order.created");

    eventBus.publish(List.of(event1, event2));

    verify(publisher).publish("cqrs.events", "test.order.created", event1, "event");
    verify(publisher).publish("cqrs.events", "test.order.created", event2, "event");
  }

  @Test
  void publishListWithAmqpExceptionShouldFallbackEachEvent() {
    TestEvent event1 = new TestEvent("data1");
    TestEvent event2 = new TestEvent("data2");
    when(rabbitNaming.exchange("events")).thenReturn("cqrs.events");
    when(messageNaming.eventName(TestEvent.class)).thenReturn("test.order.created");
    doThrow(new AmqpException("connection failed"))
        .when(publisher)
        .publish(any(), any(), any(), any());

    eventBus.publish(List.of(event1, event2));

    verify(fallbackEventBus).publish(event1);
    verify(fallbackEventBus).publish(event2);
  }
}
