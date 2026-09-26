package com.borjaglez.cqrs.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.middleware.MiddlewareChain;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.fixtures.RecordingMiddleware;
import com.borjaglez.cqrs.rabbitmq.fixtures.TestCommand;
import com.borjaglez.cqrs.rabbitmq.fixtures.ValidatedCommand;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;
import com.borjaglez.cqrs.validation.CommandValidationInterceptor;

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

  @Test
  void dispatchAndReceiveWithTypeShouldPassTypeToPublisher() {
    TestCommand command = new TestCommand("test-data");
    ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<String>() {};
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    when(publisher.publishAndReceive(
            "cqrs.commands", "test.order.create", command, "command_reply", typeRef))
        .thenReturn("typed-result");

    String result = commandBus.dispatchAndReceive(command, typeRef);

    assertThat(result).isEqualTo("typed-result");
  }

  @Test
  void dispatchAndReceiveWithTypeShouldRethrowRuntimeException() {
    TestCommand command = new TestCommand("test-data");
    ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<String>() {};
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    when(publisher.publishAndReceive(
            "cqrs.commands", "test.order.create", command, "command_reply", typeRef))
        .thenThrow(new RuntimeException("remote error"));

    assertThatThrownBy(() -> commandBus.dispatchAndReceive(command, typeRef))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("remote error");
  }

  @Test
  void dispatchAndReceiveWithTypeShouldWrapCheckedException() {
    TestCommand command = new TestCommand("test-data");
    ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<String>() {};
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    Exception checkedException = new Exception("checked error");
    when(publisher.publishAndReceive(
            "cqrs.commands", "test.order.create", command, "command_reply", typeRef))
        .thenAnswer(
            invocation -> {
              throw checkedException;
            });

    assertThatThrownBy(() -> commandBus.dispatchAndReceive(command, typeRef))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasCause(checkedException);
  }

  private RabbitMqCommandBus busWith(BusMiddleware... middlewares) {
    return new RabbitMqCommandBus(
        publisher, rabbitNaming, messageNaming, "commands", List.of(middlewares));
  }

  @Test
  void dispatchRunsOutboundMiddlewaresBeforePublishing() {
    List<String> calls = new ArrayList<>();
    TestCommand command = new TestCommand("test-data");
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    doAnswer(invocation -> calls.add("publish"))
        .when(publisher)
        .publish("cqrs.commands", "test.order.create", command, "command");

    busWith(new RecordingMiddleware(calls, "outbound", DispatchPhase.OUTBOUND)).dispatch(command);

    assertThat(calls).containsExactly("outbound", "publish");
  }

  @Test
  void requestReplyRunsOutboundMiddlewaresBeforePublishing() {
    List<String> calls = new ArrayList<>();
    TestCommand command = new TestCommand("test-data");
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    when(publisher.publishAndReceive(
            eq("cqrs.commands"), eq("test.order.create"), eq(command), any()))
        .thenAnswer(
            invocation -> {
              calls.add("publish");
              return "reply";
            });
    when(publisher.publishAndReceive(
            eq("cqrs.commands"), eq("test.order.create"), eq(command), eq("command_reply"), any()))
        .thenAnswer(
            invocation -> {
              calls.add("publish");
              return "typed-reply";
            });
    RabbitMqCommandBus bus =
        busWith(new RecordingMiddleware(calls, "outbound", DispatchPhase.OUTBOUND));

    bus.dispatchAndWait(command);
    String reply = bus.dispatchAndReceive(command);
    String typedReply =
        bus.dispatchAndReceive(command, new ParameterizedTypeReference<String>() {});

    assertThat(reply).isEqualTo("reply");
    assertThat(typedReply).isEqualTo("typed-reply");
    assertThat(calls)
        .containsExactly("outbound", "publish", "outbound", "publish", "outbound", "publish");
  }

  @Test
  void middlewaresThatDoNotDeclareOutboundDoNotRunOnTheSender() {
    List<String> calls = new ArrayList<>();
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    BusMiddleware defaultPhases =
        (message, chain) -> {
          calls.add("default");
          return chain.proceed(message);
        };

    busWith(
            new RecordingMiddleware(
                calls, "local-and-inbound", DispatchPhase.LOCAL, DispatchPhase.INBOUND),
            defaultPhases)
        .dispatch(new TestCommand("test-data"));

    assertThat(calls).isEmpty();
    verify(publisher).publish(any(), any(), any(), eq("command"));
  }

  @Test
  void invalidCommandFailsOnTheSenderWithoutPublishing() {
    RabbitMqCommandBus bus =
        busWith(
            new CommandValidationInterceptor(
                Validation.buildDefaultValidatorFactory().getValidator()));

    assertThatThrownBy(() -> bus.dispatchAndReceive(new ValidatedCommand("")))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("customerId");
    assertThatThrownBy(() -> bus.dispatch(new ValidatedCommand("")))
        .isInstanceOf(ConstraintViolationException.class);
    verifyNoInteractions(publisher);
  }

  @Test
  void checkedExceptionOfAnOutboundMiddlewareIsWrapped() {
    Exception failure = new Exception("denied");
    RabbitMqCommandBus bus = busWith(new ShortCircuit(failure));

    assertThatThrownBy(() -> bus.dispatch(new TestCommand("test-data")))
        .isInstanceOf(CommandHandlerExecutionException.class)
        .hasCause(failure);
    verifyNoInteractions(publisher);
  }

  @Test
  void remoteDispatchWithoutContextSendsTheCorrelationIdGeneratedOnTheSender() {
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    when(rabbitNaming.exchange("commands")).thenReturn("cqrs.commands");
    when(messageNaming.commandName(TestCommand.class)).thenReturn("test.order.create");
    RabbitMqCommandBus bus =
        new RabbitMqCommandBus(
            new RabbitMqPublisher(rabbitTemplate),
            rabbitNaming,
            messageNaming,
            "commands",
            List.of(new ContextPropagationMiddleware(true, List.of(), () -> "sender-id")));
    TestCommand command = new TestCommand("test-data");

    bus.dispatch(command);

    ArgumentCaptor<MessagePostProcessor> captor =
        ArgumentCaptor.forClass(MessagePostProcessor.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("cqrs.commands"), eq("test.order.create"), eq(command), captor.capture());
    Message sent =
        captor
            .getValue()
            .postProcessMessage(
                MessageBuilder.withBody(new byte[0])
                    .andProperties(new MessageProperties())
                    .build());
    assertThat((Object) sent.getMessageProperties().getHeader("cqrs.context.correlationId"))
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
