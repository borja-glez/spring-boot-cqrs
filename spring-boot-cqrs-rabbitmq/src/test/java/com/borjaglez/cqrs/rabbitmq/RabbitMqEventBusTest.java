package com.borjaglez.cqrs.rabbitmq;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.fixtures.PlainTextMessageConverter;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEvent;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

class RabbitMqEventBusTest {

  private RabbitMqPublisher publisher;
  private RabbitMqNamingStrategy rabbitNaming;
  private MessageNamingStrategy messageNaming;
  private RabbitMqEventBus eventBus;

  @BeforeEach
  void setUp() {
    publisher = mock(RabbitMqPublisher.class);
    rabbitNaming = mock(RabbitMqNamingStrategy.class);
    messageNaming = mock(MessageNamingStrategy.class);
    when(rabbitNaming.exchange("events")).thenReturn("cqrs.events");
    when(messageNaming.eventName(TestEvent.class)).thenReturn("test.order.created");
    eventBus = new RabbitMqEventBus(publisher, rabbitNaming, messageNaming, "events");
  }

  @Test
  void publishShouldSendToRabbitMq() {
    TestEvent event = new TestEvent("test-data");

    eventBus.publish(event);

    verify(publisher).publish("cqrs.events", "test.order.created", event, "event");
    verifyNoMoreInteractions(publisher);
  }

  @Test
  void publishShouldRethrowWhenBrokerIsUnavailableAndNotDeliverLocally() {
    TestEvent event = new TestEvent("test-data");
    AmqpConnectException failure = new AmqpConnectException(new ConnectException("down"));
    doThrow(failure).when(publisher).publish(any(), any(), any(), any());

    assertThatThrownBy(() -> eventBus.publish(event)).isSameAs(failure);
  }

  @Test
  void publishListShouldDelegateToSinglePublish() {
    TestEvent event1 = new TestEvent("data1");
    TestEvent event2 = new TestEvent("data2");

    eventBus.publish(List.of(event1, event2));

    verify(publisher).publish("cqrs.events", "test.order.created", event1, "event");
    verify(publisher).publish("cqrs.events", "test.order.created", event2, "event");
  }

  @Test
  void publishListShouldStopAtTheFirstFailure() {
    TestEvent event1 = new TestEvent("data1");
    TestEvent event2 = new TestEvent("data2");
    TestEvent event3 = new TestEvent("data3");
    AmqpConnectException failure = new AmqpConnectException(new ConnectException("down"));
    doThrow(failure).when(publisher).publish(any(), any(), eq(event2), any());

    assertThatThrownBy(() -> eventBus.publish(List.of(event1, event2, event3))).isSameAs(failure);

    verify(publisher).publish("cqrs.events", "test.order.created", event1, "event");
    verify(publisher, never()).publish(any(), any(), eq(event3), any());
  }

  @Test
  void publishShouldWaitForConfirmsWhenATimeoutIsGiven() {
    Duration timeout = Duration.ofSeconds(2);
    eventBus = new RabbitMqEventBus(publisher, rabbitNaming, messageNaming, "events", timeout);
    TestEvent event = new TestEvent("test-data");

    eventBus.publish(event);

    verify(publisher)
        .publishConfirmed("cqrs.events", "test.order.created", event, "event", timeout);
    verifyNoMoreInteractions(publisher);
  }

  @Test
  void publishShouldRethrowWhenTheBrokerDoesNotConfirm() {
    Duration timeout = Duration.ofSeconds(2);
    eventBus = new RabbitMqEventBus(publisher, rabbitNaming, messageNaming, "events", timeout);
    TestEvent event = new TestEvent("test-data");
    PublishNotConfirmedException failure = new PublishNotConfirmedException("nack");
    doThrow(failure).when(publisher).publishConfirmed(any(), any(), any(), any(), any());

    assertThatThrownBy(() -> eventBus.publish(event)).isSameAs(failure);
  }

  @Test
  void publishShouldFailWhenTheBrokerIsUnreachable() throws IOException {
    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost", port);
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(new PlainTextMessageConverter());
    try {
      eventBus =
          new RabbitMqEventBus(
              new RabbitMqPublisher(rabbitTemplate), rabbitNaming, messageNaming, "events");

      assertThatThrownBy(() -> eventBus.publish(new TestEvent("lost")))
          .isInstanceOf(AmqpConnectException.class);
    } finally {
      connectionFactory.destroy();
    }
  }
}
