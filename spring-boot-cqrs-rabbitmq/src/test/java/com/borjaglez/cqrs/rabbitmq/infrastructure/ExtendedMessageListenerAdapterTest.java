package com.borjaglez.cqrs.rabbitmq.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import com.rabbitmq.client.Channel;

class ExtendedMessageListenerAdapterTest {

  @Test
  void buildListenerArgumentsShouldReturnMessageAndExtractedObject() {
    MessageConverter converter = new Jackson2JsonMessageConverter();
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
    MessageConverter converter = new Jackson2JsonMessageConverter();
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
    MessageConverter converter = new Jackson2JsonMessageConverter();
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

    verify(channel).basicPublish(eq("reply-exchange"), eq("reply-key"), any(), any(byte[].class));
  }

  @Test
  void onMessageShouldRethrowWhenNoReplyTo() throws Exception {
    MessageConverter converter = new Jackson2JsonMessageConverter();
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
    MessageConverter converter = new Jackson2JsonMessageConverter();
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
    MessageConverter converter = new Jackson2JsonMessageConverter();
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
    MessageConverter converter = new Jackson2JsonMessageConverter();
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
      throw new RuntimeException("Handler failed");
    }
  }
}
