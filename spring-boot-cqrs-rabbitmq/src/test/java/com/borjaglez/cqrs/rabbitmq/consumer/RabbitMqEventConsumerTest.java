package com.borjaglez.cqrs.rabbitmq.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.registry.EventHandlerRegistry;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalEvent;
import com.borjaglez.cqrs.rabbitmq.fixtures.RecordingMiddleware;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestEvent;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqExposure;
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

    verify(registry).handleRemote(event);
  }

  @Test
  void consumeShouldHandleErrorWithRetry() {
    TestEvent event = new TestEvent("test-data");
    doThrow(new RuntimeException("handler error")).when(registry).handleRemote(event);
    when(namingStrategy.exchangeRetry("events")).thenReturn("cqrs.events.retry");

    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    consumer.consume(message, event);

    verify(rabbitTemplate).send("cqrs.events.retry", "app", message);
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

    verify(registry).handleRemote(event);
    assertThat(middlewareCalled).isTrue();
  }

  @Test
  void consumeShouldExposeContextFromHeaders() {
    java.util.concurrent.atomic.AtomicReference<String> observed =
        new java.util.concurrent.atomic.AtomicReference<>();
    BusMiddleware middleware =
        (msg, chain) -> {
          observed.set(MessageContext.current().correlationId());
          return chain.proceed(msg);
        };

    RabbitMqEventConsumer consumerWithContext =
        new RabbitMqEventConsumer(
            registry,
            List.of(middleware),
            rabbitTemplate,
            namingStrategy,
            "events",
            "app",
            "cqrs.context.");

    TestEvent event = new TestEvent("test-data");
    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.context.correlationId", "cid-ev");
    Message message = MessageBuilder.withBody("{}".getBytes()).andProperties(props).build();

    consumerWithContext.consume(message, event);

    assertThat(observed.get()).isEqualTo("cid-ev");
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  @Test
  void consumeShouldSendStraightToDeadLetterWhenMaxAttemptsIsOne() {
    TestEvent event = new TestEvent("test-data");
    doThrow(new RuntimeException("handler error")).when(registry).handleRemote(event);
    when(namingStrategy.exchangeDeadLetter("events")).thenReturn("cqrs.events.dead_letter");
    RabbitMqEventConsumer singleAttemptConsumer =
        new RabbitMqEventConsumer(
            registry,
            Collections.emptyList(),
            rabbitTemplate,
            namingStrategy,
            "events",
            "app",
            "cqrs.context.",
            1);

    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();
    singleAttemptConsumer.consume(message, event);

    verify(rabbitTemplate).send("cqrs.events.dead_letter", "app", message);
    assertThat((String) message.getMessageProperties().getHeader("cqrs.error.type"))
        .isEqualTo(RuntimeException.class.getName());
    assertThat((String) message.getMessageProperties().getHeader("cqrs.error.message"))
        .isEqualTo("handler error");
    assertThat((Integer) message.getMessageProperties().getHeader("cqrs.error.attempts"))
        .isEqualTo(1);
  }

  @Test
  void constructorShouldRejectMaxAttemptsBelowOne() {
    assertThatThrownBy(
            () ->
                new RabbitMqEventConsumer(
                    registry,
                    Collections.emptyList(),
                    rabbitTemplate,
                    namingStrategy,
                    "events",
                    "app",
                    "cqrs.context.",
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("max-attempts");
  }

  private RabbitMqEventConsumer consumerExposing(RabbitMqExposure exposure) {
    return new RabbitMqEventConsumer(
        registry,
        Collections.emptyList(),
        rabbitTemplate,
        namingStrategy,
        "events",
        "app",
        "cqrs.context.",
        3,
        exposure);
  }

  private static Message message() {
    return MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();
  }

  @Test
  void consumeShouldRejectUnannotatedEventsWithoutHandlingOrRetryingThem() {
    LocalEvent event = new LocalEvent("local");

    assertThatThrownBy(() -> consumer.consume(message(), event))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class)
        .hasMessageContaining(LocalEvent.class.getName());

    verify(registry, never()).handleRemote(any());
    verify(registry, never()).handle(any());
    verifyNoInteractions(rabbitTemplate);
  }

  @Test
  void consumeShouldRejectEventsWhoseHandlersAreAllLocalEvenWhenExposingAll() {
    TestEvent event = new TestEvent("internal");
    when(registry.getHandlerInfos(TestEvent.class))
        .thenReturn(
            List.of(new EventHandlerRegistry.HandlerInfo(new Object(), null, "e", null, false)));

    assertThatThrownBy(() -> consumerExposing(RabbitMqExposure.ALL).consume(message(), event))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);

    verify(registry, never()).handleRemote(any());
    verifyNoInteractions(rabbitTemplate);
  }

  @Test
  void consumeShouldRunOnlyRemoteHandlersOfExposedEvents() {
    LocalEvent event = new LocalEvent("local");

    consumerExposing(RabbitMqExposure.ALL).consume(message(), event);

    verify(registry).handleRemote(event);
    verify(registry, never()).handle(any());
  }

  @Test
  void runsOnlyMiddlewaresDeclaringTheInboundPhase() {
    List<String> calls = new ArrayList<>();
    RabbitMqEventConsumer inbound =
        new RabbitMqEventConsumer(
            registry, phased(calls), rabbitTemplate, namingStrategy, "events", "app");
    TestEvent event = new TestEvent("test-data");
    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    inbound.consume(message, event);

    assertThat(calls).containsExactly("inbound");
    verify(registry).handleRemote(event);
  }

  private static List<BusMiddleware> phased(List<String> calls) {
    return List.of(
        new RecordingMiddleware(calls, "inbound", DispatchPhase.INBOUND),
        new RecordingMiddleware(calls, "outbound", DispatchPhase.OUTBOUND),
        new RecordingMiddleware(calls, "local", DispatchPhase.LOCAL));
  }
}
