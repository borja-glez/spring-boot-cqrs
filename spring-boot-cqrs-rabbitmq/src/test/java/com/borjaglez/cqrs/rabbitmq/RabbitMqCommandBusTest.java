package com.borjaglez.cqrs.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestCommand;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

class RabbitMqCommandBusTest {

  private RabbitMqPublisher publisher;
  private RabbitMqNamingStrategy rabbitNaming;
  private MessageNamingStrategy messageNaming;
  private RabbitMqCommandBus commandBus;

  @BeforeEach
  void setUp() {
    publisher = mock(RabbitMqPublisher.class);
    rabbitNaming = mock(RabbitMqNamingStrategy.class);
    messageNaming = mock(MessageNamingStrategy.class);
    commandBus = new RabbitMqCommandBus(publisher, rabbitNaming, messageNaming, "commands");
  }

  @Test
  void dispatchShouldPublishToCorrectExchangeAndRoutingKey() {
    TestCommand command = new TestCommand("test-data");
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");

    commandBus.dispatch(command);

    verify(publisher).publish("cqrs.commands", "test.order.create", command, "command");
  }

  @Test
  void dispatchAndWaitShouldPublishWithCommandWaitType() {
    TestCommand command = new TestCommand("test-data");
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    when(publisher.publishAndReceive("cqrs.commands", "test.order.create", command, "command_wait"))
        .thenReturn("");

    commandBus.dispatchAndWait(command);

    verify(publisher)
        .publishAndReceive("cqrs.commands", "test.order.create", command, "command_wait");
  }

  @Test
  void dispatchAndWaitShouldRethrowRuntimeException() {
    TestCommand command = new TestCommand("test-data");
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    when(publisher.publishAndReceive("cqrs.commands", "test.order.create", command, "command_wait"))
        .thenThrow(new RuntimeException("remote error"));

    assertThatThrownBy(() -> commandBus.dispatchAndWait(command))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("remote error");
  }

  @Test
  void dispatchAndWaitShouldWrapCheckedExceptionInCommandHandlerExecutionException() {
    TestCommand command = new TestCommand("test-data");
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    Exception checkedException = new Exception("checked error");
    when(publisher.publishAndReceive("cqrs.commands", "test.order.create", command, "command_wait"))
        .thenAnswer(
            invocation -> {
              throw checkedException;
            });

    assertThatThrownBy(() -> commandBus.dispatchAndWait(command))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasCause(checkedException);
  }

  @Test
  void dispatchAndReceiveShouldReturnResult() {
    TestCommand command = new TestCommand("test-data");
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    when(publisher.publishAndReceive(
            "cqrs.commands", "test.order.create", command, "command_reply"))
        .thenReturn("result-value");

    String result = commandBus.dispatchAndReceive(command);

    assertThat(result).isEqualTo("result-value");
  }

  @Test
  void dispatchAndReceiveShouldRethrowRuntimeException() {
    TestCommand command = new TestCommand("test-data");
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    when(publisher.publishAndReceive(
            "cqrs.commands", "test.order.create", command, "command_reply"))
        .thenThrow(new RuntimeException("remote error"));

    assertThatThrownBy(() -> commandBus.dispatchAndReceive(command))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("remote error");
  }

  @Test
  void dispatchAndReceiveShouldWrapCheckedExceptionInCommandHandlerExecutionException() {
    TestCommand command = new TestCommand("test-data");
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    Exception checkedException = new Exception("checked error");
    when(publisher.publishAndReceive(
            "cqrs.commands", "test.order.create", command, "command_reply"))
        .thenAnswer(
            invocation -> {
              throw checkedException;
            });

    assertThatThrownBy(() -> commandBus.dispatchAndReceive(command))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasCause(checkedException);
  }
}
