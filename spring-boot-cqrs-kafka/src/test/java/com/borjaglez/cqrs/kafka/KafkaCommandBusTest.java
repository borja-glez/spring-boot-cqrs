package com.borjaglez.cqrs.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.kafka.fixtures.TestCommand;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestMode;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;

class KafkaCommandBusTest {

  private KafkaMessagePublisher publisher;
  private KafkaRequestReplyClient requestReplyClient;
  private KafkaTopicNamingStrategy topicNamingStrategy;
  private KafkaCommandBus commandBus;

  @BeforeEach
  void setUp() {
    publisher = mock(KafkaMessagePublisher.class);
    requestReplyClient = mock(KafkaRequestReplyClient.class);
    topicNamingStrategy = mock(KafkaTopicNamingStrategy.class);
    commandBus =
        new KafkaCommandBus(publisher, requestReplyClient, topicNamingStrategy, "commands");
    when(topicNamingStrategy.topic("commands")).thenReturn("cqrs.commands");
  }

  @Test
  void dispatchShouldPublishToConfiguredTopic() {
    TestCommand command = new TestCommand("test");

    commandBus.dispatch(command);

    verify(publisher).publish("cqrs.commands", command);
  }

  @Test
  void dispatchAndWaitShouldUseWaitMode() throws Exception {
    TestCommand command = new TestCommand("test");

    commandBus.dispatchAndWait(command);

    verify(requestReplyClient)
        .sendAndReceive(null, "cqrs.commands", command, null, KafkaRequestMode.WAIT);
  }

  @Test
  void dispatchAndWaitShouldWrapCheckedExceptions() throws Exception {
    TestCommand command = new TestCommand("test");
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.commands", command, null, KafkaRequestMode.WAIT))
        .thenAnswer(
            invocation -> {
              throw new Exception("boom");
            });

    assertThatThrownBy(() -> commandBus.dispatchAndWait(command))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasRootCauseMessage("boom");
  }

  @Test
  void dispatchAndWaitShouldRethrowRuntimeExceptions() throws Exception {
    TestCommand command = new TestCommand("test");
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.commands", command, null, KafkaRequestMode.WAIT))
        .thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> commandBus.dispatchAndWait(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  @Test
  void dispatchAndReceiveShouldReturnReply() throws Exception {
    TestCommand command = new TestCommand("test");
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.commands", command, null, KafkaRequestMode.REPLY))
        .thenReturn("done");

    String result = commandBus.dispatchAndReceive(command);

    assertThat(result).isEqualTo("done");
  }

  @Test
  void dispatchAndReceiveShouldWrapCheckedExceptions() throws Exception {
    TestCommand command = new TestCommand("test");
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.commands", command, null, KafkaRequestMode.REPLY))
        .thenAnswer(
            invocation -> {
              throw new Exception("boom");
            });

    assertThatThrownBy(() -> commandBus.dispatchAndReceive(command))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasRootCauseMessage("boom");
  }

  @Test
  void dispatchAndReceiveShouldRethrowRuntimeExceptions() throws Exception {
    TestCommand command = new TestCommand("test");
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.commands", command, null, KafkaRequestMode.REPLY))
        .thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> commandBus.dispatchAndReceive(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  @Test
  void dispatchAndReceiveWithTypeShouldPassTypeReference() throws Exception {
    TestCommand command = new TestCommand("test");
    ParameterizedTypeReference<String> responseType = new ParameterizedTypeReference<String>() {};
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.commands", command, responseType, KafkaRequestMode.REPLY))
        .thenReturn("typed");

    String result = commandBus.dispatchAndReceive(command, responseType);

    assertThat(result).isEqualTo("typed");
  }

  @Test
  void dispatchAndReceiveWithTypeShouldWrapCheckedExceptions() throws Exception {
    TestCommand command = new TestCommand("test");
    ParameterizedTypeReference<String> responseType = new ParameterizedTypeReference<String>() {};
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.commands", command, responseType, KafkaRequestMode.REPLY))
        .thenAnswer(
            invocation -> {
              throw new Exception("boom");
            });

    assertThatThrownBy(() -> commandBus.dispatchAndReceive(command, responseType))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasRootCauseMessage("boom");
  }

  @Test
  void dispatchAndReceiveWithTypeShouldRethrowRuntimeExceptions() throws Exception {
    TestCommand command = new TestCommand("test");
    ParameterizedTypeReference<String> responseType = new ParameterizedTypeReference<String>() {};
    when(requestReplyClient.sendAndReceive(
            null, "cqrs.commands", command, responseType, KafkaRequestMode.REPLY))
        .thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> commandBus.dispatchAndReceive(command, responseType))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }
}
