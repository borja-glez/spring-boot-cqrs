package com.borjaglez.cqrs.rabbitmq.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.query.QueryNotRegisteredException;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;
import com.borjaglez.cqrs.rabbitmq.fixtures.LocalQuery;
import com.borjaglez.cqrs.rabbitmq.fixtures.RecordingMiddleware;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestQuery;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqExposure;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

class RabbitMqQueryConsumerTest {

  private QueryHandlerRegistry registry;
  private RabbitMqQueryConsumer consumer;

  @BeforeEach
  void setUp() {
    registry = mock(QueryHandlerRegistry.class);
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    RabbitMqNamingStrategy namingStrategy = mock(RabbitMqNamingStrategy.class);
    consumer =
        new RabbitMqQueryConsumer(
            registry, Collections.emptyList(), rabbitTemplate, namingStrategy);
  }

  @Test
  void consumeShouldReturnRegistryResult() {
    TestQuery query = new TestQuery("test-data");
    when(registry.handle(query)).thenReturn("query-result");

    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    Object result = consumer.consume(message, query);

    assertThat(result).isEqualTo("query-result");
  }

  @Test
  void consumeShouldPropagateException() {
    TestQuery query = new TestQuery("test-data");
    when(registry.handle(query)).thenThrow(new QueryNotRegisteredException(TestQuery.class));

    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    assertThatThrownBy(() -> consumer.consume(message, query))
        .isInstanceOf(QueryNotRegisteredException.class);
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

    RabbitMqQueryConsumer consumerWithMiddleware =
        new RabbitMqQueryConsumer(
            registry,
            List.of(middleware),
            mock(RabbitTemplate.class),
            mock(RabbitMqNamingStrategy.class));

    TestQuery query = new TestQuery("test-data");
    when(registry.handle(query)).thenReturn("result");

    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    Object result = consumerWithMiddleware.consume(message, query);

    assertThat(result).isEqualTo("result");
    assertThat(middlewareCalled).isTrue();
  }

  @Test
  void consumeShouldWrapCheckedException() {
    BusMiddleware middleware =
        (msg, chain) -> {
          throw new Exception("checked error");
        };

    RabbitMqQueryConsumer consumerWithMiddleware =
        new RabbitMqQueryConsumer(
            registry,
            List.of(middleware),
            mock(RabbitTemplate.class),
            mock(RabbitMqNamingStrategy.class));

    TestQuery query = new TestQuery("test-data");
    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    assertThatThrownBy(() -> consumerWithMiddleware.consume(message, query))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(Exception.class);
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

    RabbitMqQueryConsumer consumerWithContext =
        new RabbitMqQueryConsumer(
            registry,
            List.of(middleware),
            mock(RabbitTemplate.class),
            mock(RabbitMqNamingStrategy.class),
            "cqrs.context.");

    TestQuery query = new TestQuery("test-data");
    when(registry.handle(query)).thenReturn("r");

    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.context.correlationId", "cid-q");
    Message message = MessageBuilder.withBody("{}".getBytes()).andProperties(props).build();

    Object result = consumerWithContext.consume(message, query);

    assertThat(result).isEqualTo("r");
    assertThat(observed.get()).isEqualTo("cid-q");
  }

  @Test
  void consumeShouldRejectUnannotatedQueriesWithoutHandlingThem() {
    LocalQuery query = new LocalQuery("local");
    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    assertThatThrownBy(() -> consumer.consume(message, query))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class)
        .hasMessageContaining(LocalQuery.class.getName());

    verify(registry, never()).handle(any());
  }

  @Test
  void consumeShouldRejectQueriesWhoseHandlerIsLocalEvenWhenExposingAll() {
    TestQuery query = new TestQuery("internal");
    when(registry.getHandlerInfo(TestQuery.class))
        .thenReturn(
            Optional.of(new QueryHandlerRegistry.HandlerInfo(new Object(), null, "q", false)));
    RabbitMqQueryConsumer exposingAll =
        new RabbitMqQueryConsumer(
            registry,
            Collections.emptyList(),
            mock(RabbitTemplate.class),
            mock(RabbitMqNamingStrategy.class),
            "cqrs.context.",
            RabbitMqExposure.ALL);
    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    assertThatThrownBy(() -> exposingAll.consume(message, query))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);

    verify(registry, never()).handle(any());
  }

  @Test
  void consumeShouldHandleUnannotatedQueriesWhenExposingAll() {
    LocalQuery query = new LocalQuery("local");
    when(registry.handle(query)).thenReturn("local-result");
    RabbitMqQueryConsumer exposingAll =
        new RabbitMqQueryConsumer(
            registry,
            Collections.emptyList(),
            mock(RabbitTemplate.class),
            mock(RabbitMqNamingStrategy.class),
            "cqrs.context.",
            RabbitMqExposure.ALL);
    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    assertThat(exposingAll.consume(message, query)).isEqualTo("local-result");
  }

  @Test
  void runsOnlyMiddlewaresDeclaringTheInboundPhase() {
    List<String> calls = new ArrayList<>();
    RabbitMqQueryConsumer inbound =
        new RabbitMqQueryConsumer(
            registry,
            phased(calls),
            mock(RabbitTemplate.class),
            mock(RabbitMqNamingStrategy.class));
    TestQuery query = new TestQuery("test-data");
    when(registry.handle(query)).thenReturn("query-result");
    Message message =
        MessageBuilder.withBody("{}".getBytes()).andProperties(new MessageProperties()).build();

    Object result = inbound.consume(message, query);

    assertThat(result).isEqualTo("query-result");
    assertThat(calls).containsExactly("inbound");
  }

  private static List<BusMiddleware> phased(List<String> calls) {
    return List.of(
        new RecordingMiddleware(calls, "inbound", DispatchPhase.INBOUND),
        new RecordingMiddleware(calls, "outbound", DispatchPhase.OUTBOUND),
        new RecordingMiddleware(calls, "local", DispatchPhase.LOCAL));
  }
}
