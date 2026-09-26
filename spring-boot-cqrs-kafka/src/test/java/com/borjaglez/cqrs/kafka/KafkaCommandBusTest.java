package com.borjaglez.cqrs.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;

import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.kafka.fixtures.RecordingMiddleware;
import com.borjaglez.cqrs.kafka.fixtures.TestCommand;
import com.borjaglez.cqrs.kafka.fixtures.ValidatedCommand;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaMessageKind;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaPartitionKeyStrategy;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestMode;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaRequestReplyClient;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.middleware.MiddlewareChain;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;
import com.borjaglez.cqrs.validation.CommandValidationInterceptor;

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

  private KafkaCommandBus busWith(BusMiddleware... middlewares) {
    return new KafkaCommandBus(
        publisher, requestReplyClient, topicNamingStrategy, "commands", List.of(middlewares));
  }

  @Test
  void everySendRunsOutboundMiddlewaresBeforeSending() throws Exception {
    List<String> calls = new ArrayList<>();
    TestCommand command = new TestCommand("test");
    doAnswer(invocation -> calls.add("publish")).when(publisher).publish("cqrs.commands", command);
    when(requestReplyClient.sendAndReceive(
            eq(null), eq("cqrs.commands"), eq(command), any(), any()))
        .thenAnswer(
            invocation -> {
              calls.add("send");
              return "reply";
            });
    KafkaCommandBus bus =
        busWith(new RecordingMiddleware(calls, "outbound", DispatchPhase.OUTBOUND));

    bus.dispatch(command);
    bus.dispatchAndWait(command);
    String reply = bus.dispatchAndReceive(command);
    String typed = bus.dispatchAndReceive(command, new ParameterizedTypeReference<String>() {});

    assertThat(reply).isEqualTo("reply");
    assertThat(typed).isEqualTo("reply");
    assertThat(calls)
        .containsExactly(
            "outbound", "publish", "outbound", "send", "outbound", "send", "outbound", "send");
  }

  @Test
  void middlewaresThatDoNotDeclareOutboundDoNotRunOnTheSender() {
    List<String> calls = new ArrayList<>();
    BusMiddleware defaultPhases =
        (message, chain) -> {
          calls.add("default");
          return chain.proceed(message);
        };
    TestCommand command = new TestCommand("test");

    busWith(
            new RecordingMiddleware(
                calls, "local-and-inbound", DispatchPhase.LOCAL, DispatchPhase.INBOUND),
            defaultPhases)
        .dispatch(command);

    assertThat(calls).isEmpty();
    verify(publisher).publish("cqrs.commands", command);
  }

  @Test
  void invalidCommandFailsOnTheSenderWithoutSending() {
    KafkaCommandBus bus =
        busWith(
            new CommandValidationInterceptor(
                Validation.buildDefaultValidatorFactory().getValidator()));

    assertThatThrownBy(() -> bus.dispatchAndReceive(new ValidatedCommand("")))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("customerId");
    assertThatThrownBy(() -> bus.dispatch(new ValidatedCommand("")))
        .isInstanceOf(ConstraintViolationException.class);
    verifyNoInteractions(publisher, requestReplyClient);
  }

  @Test
  void checkedExceptionOfAnOutboundMiddlewareIsWrapped() {
    Exception failure = new Exception("denied");

    assertThatThrownBy(() -> busWith(new ShortCircuit(failure)).dispatch(new TestCommand("t")))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasCause(failure);
    verifyNoInteractions(publisher);
  }

  @Test
  @SuppressWarnings("unchecked")
  void remoteDispatchWithoutContextSendsTheCorrelationIdGeneratedOnTheSender() {
    KafkaTemplate<String, byte[]> kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    MessageSerializer serializer = mock(MessageSerializer.class);
    KafkaPartitionKeyStrategy keys = mock(KafkaPartitionKeyStrategy.class);
    MessageNamingStrategy naming = mock(MessageNamingStrategy.class);
    TestCommand command = new TestCommand("test");
    when(serializer.serialize(command)).thenReturn(new byte[0]);
    when(keys.partitionKey(KafkaMessageKind.COMMAND, command)).thenReturn("key");
    when(naming.commandName(TestCommand.class)).thenReturn("test.command");
    KafkaCommandBus bus =
        new KafkaCommandBus(
            new KafkaMessagePublisher(kafkaTemplate, serializer, keys, naming),
            requestReplyClient,
            topicNamingStrategy,
            "commands",
            List.of(new ContextPropagationMiddleware(true, List.of(), () -> "sender-id")));

    bus.dispatch(command);

    ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());
    assertThat(
            new String(
                captor.getValue().headers().lastHeader("cqrs.context.correlationId").value(),
                StandardCharsets.UTF_8))
        .isEqualTo("sender-id");
    assertThat(MessageContext.current().isEmpty()).isTrue();
  }

  /** Outbound middleware that fails with a checked exception instead of calling the chain. */
  private static final class ShortCircuit implements BusMiddleware {

    private final Exception failure;

    ShortCircuit(Exception failure) {
      this.failure = failure;
    }

    @Override
    public Object process(Object message, MiddlewareChain chain) throws Exception {
      throw failure;
    }

    @Override
    public Set<DispatchPhase> phases() {
      return Set.of(DispatchPhase.OUTBOUND);
    }
  }
}
