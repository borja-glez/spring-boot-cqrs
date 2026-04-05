package com.borjaglez.cqrs.rabbitmq.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestCommand;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

class RabbitMqCommandConsumerTest {

  private CommandHandlerRegistry registry;
  private RabbitTemplate rabbitTemplate;
  private RabbitMqNamingStrategy namingStrategy;
  private RabbitMqCommandConsumer consumer;

  @BeforeEach
  void setUp() {
    registry = mock(CommandHandlerRegistry.class);
    rabbitTemplate = mock(RabbitTemplate.class);
    namingStrategy = mock(RabbitMqNamingStrategy.class);
    consumer =
        new RabbitMqCommandConsumer(
            registry, Collections.emptyList(), rabbitTemplate, namingStrategy, "commands", "app");
  }

  private Message createMessage(String messageType) {
    MessageProperties props = new MessageProperties();
    if (messageType != null) {
      props.setHeader("cqrs.message.type", messageType);
    }
    return MessageBuilder.withBody("{}".getBytes()).andProperties(props).build();
  }

  @Test
  void consumeShouldHandleFireAndForgetCommand() {
    TestCommand command = new TestCommand("test-data");
    when(registry.handle(command)).thenReturn("result");

    Message message = createMessage("command");
    Object result = consumer.consume(message, command);

    assertThat(result).isNull();
    verify(registry).handle(command);
  }

  @Test
  void consumeShouldReturnResultForCommandReply() {
    TestCommand command = new TestCommand("test-data");
    when(registry.handle(command)).thenReturn("handler-result");

    Message message = createMessage("command_reply");
    Object result = consumer.consume(message, command);

    assertThat(result).isEqualTo("handler-result");
  }

  @Test
  void consumeShouldReturnOkForCommandWait() {
    TestCommand command = new TestCommand("test-data");
    when(registry.handle(command)).thenReturn("anything");

    Message message = createMessage("command_wait");
    Object result = consumer.consume(message, command);

    assertThat(result).isEqualTo("");
  }

  @Test
  void consumeShouldDefaultToCommandTypeWhenHeaderMissing() {
    TestCommand command = new TestCommand("test-data");
    when(registry.handle(command)).thenReturn("result");

    Message message = createMessage(null);
    Object result = consumer.consume(message, command);

    assertThat(result).isNull(); // fire-and-forget behavior
  }

  @Test
  void consumeShouldRethrowExceptionForCommandReply() {
    TestCommand command = new TestCommand("test-data");
    when(registry.handle(command)).thenThrow(new RuntimeException("handler error"));

    Message message = createMessage("command_reply");

    assertThatThrownBy(() -> consumer.consume(message, command))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("handler error");
  }

  @Test
  void consumeShouldRethrowExceptionForCommandWait() {
    TestCommand command = new TestCommand("test-data");
    when(registry.handle(command)).thenThrow(new RuntimeException("handler error"));

    Message message = createMessage("command_wait");

    assertThatThrownBy(() -> consumer.consume(message, command))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("handler error");
  }

  @Test
  void consumeShouldHandleErrorWithRetryForFireAndForget() {
    TestCommand command = new TestCommand("test-data");
    when(registry.handle(command)).thenThrow(new RuntimeException("handler error"));
    when(namingStrategy.exchangeRetry("commands")).thenReturn("cqrs.commands.retry");

    Message message = createMessage("command");
    Object result = consumer.consume(message, command);

    assertThat(result).isNull();
    verify(rabbitTemplate).send("cqrs.commands.retry", "#", message);
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

    RabbitMqCommandConsumer consumerWithMiddleware =
        new RabbitMqCommandConsumer(
            registry, List.of(middleware), rabbitTemplate, namingStrategy, "commands", "app");

    TestCommand command = new TestCommand("test-data");
    when(registry.handle(command)).thenReturn("result");

    Message message = createMessage("command_reply");
    Object result = consumerWithMiddleware.consume(message, command);

    assertThat(result).isEqualTo("result");
    assertThat(middlewareCalled).isTrue();
  }

  @Test
  void consumeShouldWrapCheckedExceptionForCommandReply() {
    BusMiddleware middleware =
        (msg, chain) -> {
          throw new Exception("checked error");
        };

    RabbitMqCommandConsumer consumerWithMiddleware =
        new RabbitMqCommandConsumer(
            registry, List.of(middleware), rabbitTemplate, namingStrategy, "commands", "app");

    TestCommand command = new TestCommand("test-data");
    Message message = createMessage("command_reply");

    assertThatThrownBy(() -> consumerWithMiddleware.consume(message, command))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(Exception.class);
  }

  @Test
  void consumeShouldWrapCheckedExceptionForCommandWait() {
    BusMiddleware middleware =
        (msg, chain) -> {
          throw new Exception("checked error");
        };

    RabbitMqCommandConsumer consumerWithMiddleware =
        new RabbitMqCommandConsumer(
            registry, List.of(middleware), rabbitTemplate, namingStrategy, "commands", "app");

    TestCommand command = new TestCommand("test-data");
    Message message = createMessage("command_wait");

    assertThatThrownBy(() -> consumerWithMiddleware.consume(message, command))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(Exception.class);
  }

  @Test
  void consumeShouldRetryOnCheckedExceptionForFireAndForget() {
    BusMiddleware middleware =
        (msg, chain) -> {
          throw new Exception("checked error");
        };
    when(namingStrategy.exchangeRetry("commands")).thenReturn("cqrs.commands.retry");

    RabbitMqCommandConsumer consumerWithMiddleware =
        new RabbitMqCommandConsumer(
            registry, List.of(middleware), rabbitTemplate, namingStrategy, "commands", "app");

    TestCommand command = new TestCommand("test-data");
    Message message = createMessage("command");
    Object result = consumerWithMiddleware.consume(message, command);

    assertThat(result).isNull();
    verify(rabbitTemplate).send("cqrs.commands.retry", "#", message);
  }
}
