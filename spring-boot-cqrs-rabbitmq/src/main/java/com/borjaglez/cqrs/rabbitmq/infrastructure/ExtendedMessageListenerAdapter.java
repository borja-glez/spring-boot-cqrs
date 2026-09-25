package com.borjaglez.cqrs.rabbitmq.infrastructure;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.core.Address;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import com.rabbitmq.client.Channel;

public class ExtendedMessageListenerAdapter extends MessageListenerAdapter {

  private static final Log LOG = LogFactory.getLog(ExtendedMessageListenerAdapter.class);

  static final String HEADER_ERROR = "cqrs.error";
  static final String HEADER_ERROR_TYPE = "cqrs.error.type";
  static final String HEADER_NULL_RESULT = "cqrs.result.null";

  /** Stands for a {@code null} result, which Spring AMQP would otherwise not answer at all. */
  static final Object NULL_RESULT = new Object();

  private final MessagePropertiesConverter propertiesConverter =
      new DefaultMessagePropertiesConverter();

  public ExtendedMessageListenerAdapter(
      Object delegate, MessageConverter converter, String methodName) {
    super(delegate, methodName);
    setMessageConverter(converter);
  }

  @Override
  protected Object[] buildListenerArguments(
      Object extractedMessage, Channel channel, Message message) {
    return new Object[] {message, extractedMessage};
  }

  /**
   * A handler that returns {@code null} to a request that expects a reply still gets one, so the
   * requester can tell a {@code null} result from a missing reply.
   */
  @Override
  protected Object invokeListenerMethod(
      String methodName, Object[] arguments, Message originalMessage) {
    Object result = super.invokeListenerMethod(methodName, arguments, originalMessage);
    String replyTo = originalMessage.getMessageProperties().getReplyTo();
    if (result == null && replyTo != null && !replyTo.isEmpty()) {
      return NULL_RESULT;
    }
    return result;
  }

  @Override
  protected Message buildMessage(Channel channel, Object result, Type genericType) {
    if (result == NULL_RESULT) {
      MessageProperties properties = new MessageProperties();
      properties.setHeader(HEADER_NULL_RESULT, true);
      return MessageBuilder.withBody(new byte[0]).andProperties(properties).build();
    }
    return super.buildMessage(channel, result, genericType);
  }

  @Override
  public void onMessage(Message message, Channel channel) throws Exception {
    try {
      super.onMessage(message, channel);
    } catch (Exception e) {
      String replyTo = message.getMessageProperties().getReplyTo();
      if (replyTo != null && !replyTo.isEmpty()) {
        sendErrorResponse(channel, message, e);
      } else {
        throw e;
      }
    }
  }

  /**
   * Replies with the handler's failure. The reply carries the request's correlation id, without
   * which the requester never matches it, and the type of the exception the handler threw.
   */
  private void sendErrorResponse(Channel channel, Message originalMessage, Exception error) {
    try {
      Throwable failure = handlerFailure(error);
      String errorBody =
          failure.getMessage() != null ? failure.getMessage() : failure.getClass().getName();

      MessageProperties replyProperties = new MessageProperties();
      replyProperties.setHeader(HEADER_ERROR, true);
      replyProperties.setHeader(HEADER_ERROR_TYPE, failure.getClass().getName());
      replyProperties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
      replyProperties.setContentEncoding(StandardCharsets.UTF_8.name());

      String correlationId = originalMessage.getMessageProperties().getCorrelationId();
      if (correlationId != null) {
        replyProperties.setCorrelationId(correlationId);
      }

      Address replyAddress = new Address(originalMessage.getMessageProperties().getReplyTo());
      channel.basicPublish(
          replyAddress.getExchangeName(),
          replyAddress.getRoutingKey(),
          propertiesConverter.fromMessageProperties(replyProperties, StandardCharsets.UTF_8.name()),
          errorBody.getBytes(StandardCharsets.UTF_8));
    } catch (Exception publishError) {
      LOG.warn("Could not send the error reply; the requester will time out", publishError);
    }
  }

  /** The exception thrown by the handler, without the wrapper added by the listener adapter. */
  static Throwable handlerFailure(Throwable error) {
    Throwable current = error;
    while (current instanceof ListenerExecutionFailedException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }
}
