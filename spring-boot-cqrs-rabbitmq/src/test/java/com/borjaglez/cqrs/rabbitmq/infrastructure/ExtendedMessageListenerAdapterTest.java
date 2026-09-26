package com.borjaglez.cqrs.rabbitmq.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

class ExtendedMessageListenerAdapterTest {

  @Test
  void buildListenerArgumentsShouldReturnMessageAndExtractedObject() {
    MessageConverter converter = JsonMessageConverterFactory.create();
    TestDelegate delegate = new TestDelegate();
    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(delegate, converter, "handle");

    MessageProperties props = new MessageProperties();
    Message message = MessageBuilder.withBody("test".getBytes()).andProperties(props).build();
    Channel channel = mock(Channel.class);

    Object[] args = adapter.buildListenerArguments("extracted", channel, message);

    assertThat(args).hasSize(2);
    assertThat(args[0]).isEqualTo(message);
    assertThat(args[1]).isEqualTo("extracted");
  }

  @Test
  void onMessageShouldDelegateToSuper() throws Exception {
    MessageConverter converter = JsonMessageConverterFactory.create();
    TestDelegate delegate = new TestDelegate();
    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(delegate, converter, "handle");

    MessageProperties props = new MessageProperties();
    props.setContentType("application/json");
    Message message = MessageBuilder.withBody("\"hello\"".getBytes()).andProperties(props).build();
    Channel channel = mock(Channel.class);

    // This will invoke through super.onMessage which calls buildListenerArguments
    // The delegate.handle(Message, Object) should be called
    adapter.onMessage(message, channel);

    assertThat(delegate.lastMessage).isNotNull();
  }

  @Test
  void onMessageShouldSendErrorResponseWhenReplyToIsSetAndExceptionOccurs() throws Exception {
    MessageConverter converter = JsonMessageConverterFactory.create();
    FailingDelegate delegate = new FailingDelegate();
    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(delegate, converter, "handle");

    MessageProperties props = new MessageProperties();
    props.setContentType("application/json");
    props.setReplyTo("reply-exchange/reply-key");
    props.setCorrelationId("corr-123");
    Message message = MessageBuilder.withBody("\"hello\"".getBytes()).andProperties(props).build();
    Channel channel = mock(Channel.class);

    // Should not throw because replyTo is set - error is sent as response
    adapter.onMessage(message, channel);

    ArgumentCaptor<AMQP.BasicProperties> properties =
        ArgumentCaptor.forClass(AMQP.BasicProperties.class);
    ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
    verify(channel)
        .basicPublish(eq("reply-exchange"), eq("reply-key"), properties.capture(), body.capture());
    // Without the correlation id the requester never matches the reply (finding C1).
    assertThat(properties.getValue().getCorrelationId()).isEqualTo("corr-123");
    assertThat(properties.getValue().getHeaders()).containsEntry("cqrs.error", true);
    assertThat(properties.getValue().getHeaders().get("cqrs.error.type").toString())
        .isEqualTo(IllegalStateException.class.getName());
    assertThat(new String(body.getValue(), StandardCharsets.UTF_8)).isEqualTo("Handler failed");
  }

  @Test
  void onMessageShouldRethrowWhenNoReplyTo() throws Exception {
    MessageConverter converter = JsonMessageConverterFactory.create();
    FailingDelegate delegate = new FailingDelegate();
    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(delegate, converter, "handle");

    MessageProperties props = new MessageProperties();
    props.setContentType("application/json");
    Message message = MessageBuilder.withBody("\"hello\"".getBytes()).andProperties(props).build();
    Channel channel = mock(Channel.class);

    assertThatThrownBy(() -> adapter.onMessage(message, channel)).isInstanceOf(Exception.class);
  }

  @Test
  void onMessageShouldHandleErrorResponsePublishFailure() throws Exception {
    MessageConverter converter = JsonMessageConverterFactory.create();
    FailingDelegate delegate = new FailingDelegate();
    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(delegate, converter, "handle");

    MessageProperties props = new MessageProperties();
    props.setContentType("application/json");
    props.setReplyTo("reply-exchange/reply-key");
    Message message = MessageBuilder.withBody("\"hello\"".getBytes()).andProperties(props).build();
    Channel channel = mock(Channel.class);
    org.mockito.Mockito.doThrow(new IOException("publish failed"))
        .when(channel)
        .basicPublish(any(), any(), any(), any(byte[].class));

    // Should not throw even when error response publish fails
    adapter.onMessage(message, channel);
  }

  @Test
  void onMessageShouldHandleEmptyReplyTo() throws Exception {
    MessageConverter converter = JsonMessageConverterFactory.create();
    FailingDelegate delegate = new FailingDelegate();
    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(delegate, converter, "handle");

    MessageProperties props = new MessageProperties();
    props.setContentType("application/json");
    props.setReplyTo("");
    Message message = MessageBuilder.withBody("\"hello\"".getBytes()).andProperties(props).build();
    Channel channel = mock(Channel.class);

    // Empty replyTo should rethrow the exception
    assertThatThrownBy(() -> adapter.onMessage(message, channel)).isInstanceOf(Exception.class);
  }

  /** Test delegate that records invocations. */
  public static class TestDelegate {
    Message lastMessage;

    public void handle(Message message, Object payload) {
      this.lastMessage = message;
    }
  }

  @Test
  void sendErrorResponseShouldUseClassNameWhenErrorMessageIsNull() throws Exception {
    MessageConverter converter = JsonMessageConverterFactory.create();
    TestDelegate delegate = new TestDelegate();
    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(delegate, converter, "handle");

    MessageProperties props = new MessageProperties();
    props.setReplyTo("reply-exchange/reply-key");
    props.setCorrelationId("corr-789");
    Message originalMessage =
        MessageBuilder.withBody("test".getBytes()).andProperties(props).build();
    Channel channel = mock(Channel.class);

    // Use an exception whose getMessage() returns null
    Exception nullMsgError = new RuntimeException((String) null);

    Method sendErrorResponse =
        ExtendedMessageListenerAdapter.class.getDeclaredMethod(
            "sendErrorResponse", Channel.class, Message.class, Exception.class);
    sendErrorResponse.setAccessible(true);
    sendErrorResponse.invoke(adapter, channel, originalMessage, nullMsgError);

    verify(channel).basicPublish(eq("reply-exchange"), eq("reply-key"), any(), any(byte[].class));
  }

  /** Test delegate that always throws. */
  public static class FailingDelegate {
    public void handle(Message message, Object payload) {
      throw new IllegalStateException("Handler failed");
    }
  }

  @Test
  void aNullResultIsAnsweredWhenTheRequesterWaitsForAReply() {
    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(
            new NullDelegate(), JsonMessageConverterFactory.create(), "handle");
    MessageProperties props = new MessageProperties();
    props.setReplyTo("reply-exchange/reply-key");
    Message request = MessageBuilder.withBody("x".getBytes()).andProperties(props).build();

    Object result = adapter.invokeListenerMethod("handle", new Object[] {request, "x"}, request);

    assertThat(result).isSameAs(ExtendedMessageListenerAdapter.NULL_RESULT);
    Message reply = adapter.buildMessage(mock(Channel.class), result, null);
    assertThat(reply.getBody()).isEmpty();
    assertThat((Object) reply.getMessageProperties().getHeader("cqrs.result.null")).isEqualTo(true);
  }

  @Test
  void aNullResultIsNotAnsweredWithoutReplyAddress() {
    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(
            new NullDelegate(), JsonMessageConverterFactory.create(), "handle");
    Message noReplyTo = MessageBuilder.withBody("x".getBytes()).build();
    MessageProperties emptyProps = new MessageProperties();
    emptyProps.setReplyTo("");
    Message emptyReplyTo =
        MessageBuilder.withBody("x".getBytes()).andProperties(emptyProps).build();

    assertThat(adapter.invokeListenerMethod("handle", new Object[] {noReplyTo, "x"}, noReplyTo))
        .isNull();
    assertThat(
            adapter.invokeListenerMethod("handle", new Object[] {emptyReplyTo, "x"}, emptyReplyTo))
        .isNull();
  }

  @Test
  void otherResultsAreConvertedAsUsual() {
    ExtendedMessageListenerAdapter adapter =
        new ExtendedMessageListenerAdapter(
            new NullDelegate(), JsonMessageConverterFactory.create(), "handle");

    Message reply = adapter.buildMessage(mock(Channel.class), "done", String.class);

    assertThat(new String(reply.getBody(), StandardCharsets.UTF_8)).isEqualTo("\"done\"");
  }

  /** Test delegate that returns nothing. */
  public static class NullDelegate {
    public Object handle(Message message, Object payload) {
      return null;
    }
  }
}
