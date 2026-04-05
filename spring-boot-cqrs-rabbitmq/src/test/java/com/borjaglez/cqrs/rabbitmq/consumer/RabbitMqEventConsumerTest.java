package com.borjaglez.cqrs.rabbitmq.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEvent;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

class RabbitMqEventConsumerTest {

  private EventHandlerRegistry registry;
  private RabbitTemplate rabbitTemplate;
  private RabbitMqNamingStrategy namingStrategy;
  private RabbitMqEventConsumer consumer;

  @BeforeEach
  void setUp() {
    registry = mock(EventHandlerRegistry.class);
    rabbitTemplate = mock(RabbitTemplate.class);
    namingStrategy = mock(RabbitMqNamingStrategy.class);
    consumer =
        new RabbitMqEventConsumer(
            registry, Collections.emptyList(), rabbitTemplate, namingStrategy, "events", "app");
  }

  @Test
  void consumeShouldDelegateToRegistry() {
    TestEvent event = new TestEvent("test-data");
    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    consumer.consume(message, event);

    verify(registry).handle(event);
  }

  @Test
  void consumeShouldHandleErrorWithRetry() {
    TestEvent event = new TestEvent("test-data");
    doThrow(new RuntimeException("handler error")).when(registry).handle(event);
    when(namingStrategy.exchangeRetry("events")).thenReturn("cqrs.events.retry");

    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    consumer.consume(message, event);

    verify(rabbitTemplate).send("cqrs.events.retry", "#", message);
  }

  @Test
  void consumeShouldExecuteMiddlewareChain() {
    java.util.concurrent.atomic.AtomicBoolean middlewareCalled =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    BusMiddleware middleware =
        (msg, chain) -> {
          middlewareCalled.set(true);
          return chain.proceed(msg);
        };

    RabbitMqEventConsumer consumerWithMiddleware =
        new RabbitMqEventConsumer(
            registry, List.of(middleware), rabbitTemplate, namingStrategy, "events", "app");

    TestEvent event = new TestEvent("test-data");
    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    consumerWithMiddleware.consume(message, event);

    verify(registry).handle(event);
    assertThat(middlewareCalled).isTrue();
  }
}
