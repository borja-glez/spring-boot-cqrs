package com.borjaglez.cqrs.rabbitmq.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.context.MessageContextTaskDecorator;
import com.borjaglez.cqrs.rabbitmq.PublishNotConfirmedException;
import com.borjaglez.cqrs.rabbitmq.RemoteHandlerException;
import com.borjaglez.cqrs.rabbitmq.RemoteReplyTimeoutException;

class RabbitMqPublisherTest {

  private RabbitTemplate rabbitTemplate;
  private RabbitMqPublisher publisher;

  @BeforeEach
  void setUp() {
    rabbitTemplate = mock(RabbitTemplate.class);
    publisher = new RabbitMqPublisher(rabbitTemplate);
  }

  @Test
  void publishShouldSendMessageWithCorrectHeaders() {
    Object payload = "test-payload";

    publisher.publish("my-exchange", "routing.key", payload, "command");

    ArgumentCaptor<MessagePostProcessor> postProcessorCaptor =
        ArgumentCaptor.forClass(MessagePostProcessor.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("my-exchange"), eq("routing.key"), eq(payload), postProcessorCaptor.capture());

    // Verify the post processor sets the header
    MessagePostProcessor postProcessor = postProcessorCaptor.getValue();
    Message testMessage =
        MessageBuilder.withBody("test".getBytes()).andProperties(new MessageProperties()).build();
    Message processedMessage = postProcessor.postProcessMessage(testMessage);
    Object headerValue = processedMessage.getMessageProperties().getHeader("cqrs.message.type");
    assertThat(headerValue).isEqualTo("command");
    assertThat((Object) processedMessage.getMessageProperties().getHeader("cqrs.message.name"))
        .isEqualTo("routing.key");
  }

  @Test
  void publishAndReceiveShouldReturnDeserializedResult() {
    MessageConverter converter = mock(MessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    MessageProperties requestProps = new MessageProperties();
    Message requestMessage =
        MessageBuilder.withBody("request".getBytes()).andProperties(requestProps).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);

    MessageProperties replyProps = new MessageProperties();
    Message replyMessage =
        MessageBuilder.withBody("reply".getBytes()).andProperties(replyProps).build();
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any())).thenReturn(replyMessage);

    String expectedResult = "deserialized-result";
    when(converter.fromMessage(replyMessage)).thenReturn(expectedResult);

    Object result = publisher.publishAndReceive("exchange", "key", "payload", "command_reply");

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void publishAndReceiveShouldTimeOutWhenNoReply() {
    MessageConverter converter = mock(MessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    MessageProperties requestProps = new MessageProperties();
    Message requestMessage =
        MessageBuilder.withBody("request".getBytes()).andProperties(requestProps).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any(Message.class)))
        .thenReturn(null);

    assertThatThrownBy(() -> invoke())
        .isInstanceOf(RemoteReplyTimeoutException.class)
        .hasMessageContaining("key");
  }

  @Test
  void publishAndReceiveShouldThrowOnErrorReply() {
    MessageConverter converter = mock(MessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    MessageProperties requestProps = new MessageProperties();
    Message requestMessage =
        MessageBuilder.withBody("request".getBytes()).andProperties(requestProps).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);

    MessageProperties replyProps = new MessageProperties();
    replyProps.setHeader("cqrs.error", true);
    Message errorReply =
        MessageBuilder.withBody("Something went wrong".getBytes())
            .andProperties(replyProps)
            .build();
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any(Message.class)))
        .thenReturn(errorReply);

    assertThatThrownBy(
            () -> publisher.publishAndReceive("exchange", "key", "payload", "command_reply"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Remote handler error: Something went wrong");
  }

  @Test
  void publishAndReceiveShouldReturnNullForAnExplicitNullResult() {
    MessageConverter converter = mock(MessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);
    when(converter.toMessage(any(), any()))
        .thenReturn(MessageBuilder.withBody(new byte[0]).build());
    MessageProperties replyProps = new MessageProperties();
    replyProps.setHeader("cqrs.result.null", true);
    Message nullReply = MessageBuilder.withBody(new byte[0]).andProperties(replyProps).build();
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any(Message.class)))
        .thenReturn(nullReply);

    assertThat(publisher.publishAndReceive("exchange", "key", "payload", "command_reply")).isNull();
    assertThat(
            publisher.publishAndReceive(
                "exchange", "key", "payload", "query", new ParameterizedTypeReference<String>() {}))
        .isNull();
  }

  private Object invoke() {
    return publisher.publishAndReceive("exchange", "key", "payload", "command_reply");
  }

  private Object invokeWithType() {
    return publisher.publishAndReceive(
        "exchange", "key", "payload", "query", new ParameterizedTypeReference<String>() {});
  }

  @Test
  void checkErrorShouldThrowWhenErrorHeaderIsTrue() {
    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.error", true);
    Message message =
        MessageBuilder.withBody("error details".getBytes()).andProperties(props).build();

    assertThatThrownBy(() -> publisher.checkError(message))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Remote handler error: error details");
  }

  @Test
  void checkErrorShouldCarryTheRemoteExceptionType() {
    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.error", true);
    props.setHeader("cqrs.error.type", "com.example.OutOfStockException");
    Message message =
        MessageBuilder.withBody("sin stock: café".getBytes(StandardCharsets.UTF_8))
            .andProperties(props)
            .build();

    assertThatThrownBy(() -> publisher.checkError(message))
        .isInstanceOfSatisfying(
            RemoteHandlerException.class,
            e -> {
              assertThat(e.getRemoteExceptionType()).isEqualTo("com.example.OutOfStockException");
              assertThat(e.getMessage()).isEqualTo("Remote handler error: sin stock: café");
            });
  }

  @Test
  void checkErrorWithoutTypeHeaderLeavesTheTypeUnknown() {
    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.error", true);
    Message message = MessageBuilder.withBody("boom".getBytes()).andProperties(props).build();

    assertThatThrownBy(() -> publisher.checkError(message))
        .isInstanceOfSatisfying(
            RemoteHandlerException.class, e -> assertThat(e.getRemoteExceptionType()).isNull());
  }

  @Test
  void checkErrorShouldNotThrowWhenErrorHeaderIsFalse() {
    MessageProperties props = new MessageProperties();
    props.setHeader("cqrs.error", false);
    Message message = MessageBuilder.withBody("data".getBytes()).andProperties(props).build();

    publisher.checkError(message); // should not throw
  }

  @Test
  void checkErrorShouldNotThrowWhenErrorHeaderIsMissing() {
    MessageProperties props = new MessageProperties();
    Message message = MessageBuilder.withBody("data".getBytes()).andProperties(props).build();

    publisher.checkError(message); // should not throw
  }

  @Test
  void publishAndReceiveWithTypeReferenceShouldUseSmartConverter() {
    SmartMessageConverter converter = mock(SmartMessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    MessageProperties requestProps = new MessageProperties();
    Message requestMessage =
        MessageBuilder.withBody("request".getBytes()).andProperties(requestProps).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);

    MessageProperties replyProps = new MessageProperties();
    Message replyMessage =
        MessageBuilder.withBody("reply".getBytes()).andProperties(replyProps).build();
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any())).thenReturn(replyMessage);

    ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<String>() {};
    when(converter.fromMessage(replyMessage, typeRef)).thenReturn("typed-result");

    Object result = publisher.publishAndReceive("exchange", "key", "payload", "query", typeRef);

    assertThat(result).isEqualTo("typed-result");
    verify(converter).fromMessage(replyMessage, typeRef);
  }

  @Test
  void publishAndReceiveWithTypeReferenceFallsBackWhenNotSmart() {
    MessageConverter converter = mock(MessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    MessageProperties requestProps = new MessageProperties();
    Message requestMessage =
        MessageBuilder.withBody("request".getBytes()).andProperties(requestProps).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);

    MessageProperties replyProps = new MessageProperties();
    Message replyMessage =
        MessageBuilder.withBody("reply".getBytes()).andProperties(replyProps).build();
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any())).thenReturn(replyMessage);

    when(converter.fromMessage(replyMessage)).thenReturn("fallback-result");

    ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<String>() {};
    Object result = publisher.publishAndReceive("exchange", "key", "payload", "query", typeRef);

    assertThat(result).isEqualTo("fallback-result");
    verify(converter).fromMessage(replyMessage);
  }

  @Test
  void publishAndReceiveWithNullTypeReferenceShouldFallBack() {
    SmartMessageConverter converter = mock(SmartMessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    MessageProperties requestProps = new MessageProperties();
    Message requestMessage =
        MessageBuilder.withBody("request".getBytes()).andProperties(requestProps).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);

    MessageProperties replyProps = new MessageProperties();
    Message replyMessage =
        MessageBuilder.withBody("reply".getBytes()).andProperties(replyProps).build();
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any())).thenReturn(replyMessage);

    when(converter.fromMessage(replyMessage)).thenReturn("untyped-result");

    Object result = publisher.publishAndReceive("exchange", "key", "payload", "query", null);

    assertThat(result).isEqualTo("untyped-result");
    verify(converter).fromMessage(replyMessage);
  }

  @Test
  void publishAndReceiveWithTypeReferenceShouldTimeOutWhenNoReply() {
    MessageConverter converter = mock(MessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    MessageProperties requestProps = new MessageProperties();
    Message requestMessage =
        MessageBuilder.withBody("request".getBytes()).andProperties(requestProps).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any(Message.class)))
        .thenReturn(null);

    assertThatThrownBy(() -> invokeWithType())
        .isInstanceOf(RemoteReplyTimeoutException.class)
        .hasMessageContaining("key");
  }

  @Test
  void publishAndReceiveWithTypeReferenceShouldThrowOnErrorReply() {
    MessageConverter converter = mock(MessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    MessageProperties requestProps = new MessageProperties();
    Message requestMessage =
        MessageBuilder.withBody("request".getBytes()).andProperties(requestProps).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);

    MessageProperties replyProps = new MessageProperties();
    replyProps.setHeader("cqrs.error", true);
    Message errorReply =
        MessageBuilder.withBody("Something went wrong".getBytes())
            .andProperties(replyProps)
            .build();
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("key"), any(Message.class)))
        .thenReturn(errorReply);

    ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<String>() {};
    assertThatThrownBy(
            () ->
                publisher.publishAndReceive("exchange", "key", "payload", "command_reply", typeRef))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Remote handler error: Something went wrong");
  }

  @Test
  void publishPropagatesCurrentContextAsHeaders() {
    publisher = new RabbitMqPublisher(rabbitTemplate, "cqrs.context.");

    MessageContext ctx =
        MessageContext.empty().with("correlationId", "abc").with("tenantId", "acme");
    try (MessageContext.Scope ignored = MessageContext.scope(ctx)) {
      publisher.publish("exchange", "key", "payload", "command");
    }

    ArgumentCaptor<MessagePostProcessor> captor =
        ArgumentCaptor.forClass(MessagePostProcessor.class);
    verify(rabbitTemplate)
        .convertAndSend(eq("exchange"), eq("key"), eq("payload"), captor.capture());

    Message processed =
        captor
            .getValue()
            .postProcessMessage(
                MessageBuilder.withBody("x".getBytes())
                    .andProperties(new MessageProperties())
                    .build());
    assertThat((Object) processed.getMessageProperties().getHeader("cqrs.context.correlationId"))
        .isEqualTo("abc");
    assertThat((Object) processed.getMessageProperties().getHeader("cqrs.context.tenantId"))
        .isEqualTo("acme");
    assertThat((Object) processed.getMessageProperties().getHeader("cqrs.message.type"))
        .isEqualTo("command");
  }

  @Test
  void publishFromADecoratedExecutorTaskCarriesTheCallerContext() throws Exception {
    publisher = new RabbitMqPublisher(rabbitTemplate, "cqrs.context.");
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setTaskDecorator(new MessageContextTaskDecorator());
    executor.initialize();
    try (MessageContext.Scope ignored =
        MessageContext.scope(MessageContext.empty().with("correlationId", "req-1"))) {
      executor.submit(() -> publisher.publish("exchange", "key", "payload", "command")).get();
    } finally {
      executor.shutdown();
    }

    ArgumentCaptor<MessagePostProcessor> captor =
        ArgumentCaptor.forClass(MessagePostProcessor.class);
    verify(rabbitTemplate)
        .convertAndSend(eq("exchange"), eq("key"), eq("payload"), captor.capture());
    Message processed =
        captor
            .getValue()
            .postProcessMessage(
                MessageBuilder.withBody("x".getBytes())
                    .andProperties(new MessageProperties())
                    .build());
    assertThat((Object) processed.getMessageProperties().getHeader("cqrs.context.correlationId"))
        .isEqualTo("req-1");
  }

  @Test
  void publishAndReceiveAddsContextHeadersOnRequest() {
    publisher = new RabbitMqPublisher(rabbitTemplate, null);

    MessageConverter converter = mock(MessageConverter.class);
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);

    Message requestMessage =
        MessageBuilder.withBody("req".getBytes()).andProperties(new MessageProperties()).build();
    when(converter.toMessage(any(), any())).thenReturn(requestMessage);
    MessageProperties nullResult = new MessageProperties();
    nullResult.setHeader("cqrs.result.null", true);
    when(rabbitTemplate.sendAndReceive(any(), any(), any(Message.class)))
        .thenReturn(MessageBuilder.withBody(new byte[0]).andProperties(nullResult).build());

    MessageContext ctx = MessageContext.empty().with("correlationId", "cid-1");
    ArgumentCaptor<MessageProperties> propsCaptor =
        ArgumentCaptor.forClass(MessageProperties.class);
    try (MessageContext.Scope ignored = MessageContext.scope(ctx)) {
      publisher.publishAndReceive("ex", "key", "payload", "command_reply");
    }
    verify(converter).toMessage(eq("payload"), propsCaptor.capture());
    assertThat((Object) propsCaptor.getValue().getHeader("cqrs.context.correlationId"))
        .isEqualTo("cid-1");

    ParameterizedTypeReference<String> typeRef = new ParameterizedTypeReference<String>() {};
    MessageContext ctx2 = MessageContext.empty().with("correlationId", "cid-2");
    try (MessageContext.Scope ignored = MessageContext.scope(ctx2)) {
      publisher.publishAndReceive("ex", "key", "payload", "query", typeRef);
    }
    verify(converter, org.mockito.Mockito.atLeastOnce()).toMessage(any(), propsCaptor.capture());
    assertThat((Object) propsCaptor.getValue().getHeader("cqrs.context.correlationId"))
        .isEqualTo("cid-2");
  }

  @Test
  void publishConfirmedShouldReturnWhenTheBrokerAcks() {
    answerConfirm(correlation -> correlation.getFuture().complete(confirm(true, null)));

    MessageContext ctx = MessageContext.empty().with("correlationId", "abc");
    try (MessageContext.Scope ignored = MessageContext.scope(ctx)) {
      publisher.publishConfirmed("ex", "key", "payload", "event", Duration.ofSeconds(1));
    }

    ArgumentCaptor<MessagePostProcessor> captor =
        ArgumentCaptor.forClass(MessagePostProcessor.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("ex"), eq("key"), eq("payload"), captor.capture(), any(CorrelationData.class));
    Message processed =
        captor
            .getValue()
            .postProcessMessage(
                MessageBuilder.withBody("x".getBytes())
                    .andProperties(new MessageProperties())
                    .build());
    assertThat((Object) processed.getMessageProperties().getHeader("cqrs.message.type"))
        .isEqualTo("event");
    assertThat((Object) processed.getMessageProperties().getHeader("cqrs.context.correlationId"))
        .isEqualTo("abc");
  }

  @Test
  void publishConfirmedShouldThrowWhenTheBrokerNacks() {
    answerConfirm(correlation -> correlation.getFuture().complete(confirm(false, "queue full")));

    assertThatThrownBy(
            () ->
                publisher.publishConfirmed("ex", "key", "payload", "event", Duration.ofSeconds(1)))
        .isInstanceOf(PublishNotConfirmedException.class)
        .hasMessage("Message key sent to ex was rejected by the broker: queue full");
  }

  @Test
  void publishConfirmedShouldThrowWhenTheConfirmTimesOut() {
    assertThatThrownBy(
            () ->
                publisher.publishConfirmed("ex", "key", "payload", "event", Duration.ofMillis(10)))
        .isInstanceOf(PublishNotConfirmedException.class)
        .hasMessage("Message key sent to ex was not confirmed by the broker within PT0.01S")
        .hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  void publishConfirmedShouldThrowWhenTheConfirmFails() {
    IllegalStateException failure = new IllegalStateException("boom");
    answerConfirm(correlation -> correlation.getFuture().completeExceptionally(failure));

    assertThatThrownBy(
            () ->
                publisher.publishConfirmed("ex", "key", "payload", "event", Duration.ofSeconds(1)))
        .isInstanceOf(PublishNotConfirmedException.class)
        .hasMessage("Message key sent to ex was not confirmed by the broker")
        .hasCause(failure);
  }

  @Test
  void publishConfirmedShouldKeepTheInterruptFlagWhenInterrupted() {
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(
              () ->
                  publisher.publishConfirmed(
                      "ex", "key", "payload", "event", Duration.ofSeconds(1)))
          .isInstanceOf(PublishNotConfirmedException.class)
          .hasMessageContaining("interrupted")
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  private void answerConfirm(java.util.function.Consumer<CorrelationData> action) {
    doAnswer(
            invocation -> {
              action.accept(invocation.getArgument(4));
              return null;
            })
        .when(rabbitTemplate)
        .convertAndSend(
            any(String.class),
            any(String.class),
            any(Object.class),
            any(MessagePostProcessor.class),
            any(CorrelationData.class));
  }

  private static CorrelationData.Confirm confirm(boolean ack, String reason) {
    return new CorrelationData.Confirm(ack, reason);
  }

  @Test
  void publishAndReceiveSendsTheMessageName() {
    MessageConverter converter = JsonMessageConverterFactory.create();
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);
    ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("svc.1.query.mod.get"), sent.capture()))
        .thenReturn(converter.toMessage("result", new MessageProperties()));

    publisher.publishAndReceive("exchange", "svc.1.query.mod.get", "payload", "query");

    assertThat((Object) sent.getValue().getMessageProperties().getHeader("cqrs.message.name"))
        .isEqualTo("svc.1.query.mod.get");
  }

  @Test
  void publishAndReceiveWithResponseTypeSendsTheMessageName() {
    MessageConverter converter = JsonMessageConverterFactory.create();
    when(rabbitTemplate.getMessageConverter()).thenReturn(converter);
    ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
    when(rabbitTemplate.sendAndReceive(eq("exchange"), eq("svc.1.query.mod.get"), sent.capture()))
        .thenReturn(converter.toMessage("result", new MessageProperties()));

    publisher.publishAndReceive(
        "exchange",
        "svc.1.query.mod.get",
        "payload",
        "query",
        new ParameterizedTypeReference<String>() {});

    assertThat((Object) sent.getValue().getMessageProperties().getHeader("cqrs.message.name"))
        .isEqualTo("svc.1.query.mod.get");
  }
}
