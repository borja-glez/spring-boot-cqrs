package com.borjaglez.cqrs.rabbitmq.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
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
        new RabbitMqCommandConsumer(registry, rabbitTemplate, namingStrategy, "commands", "app");
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

    assertThat(result).isEqualTo("OK");
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
}
