package com.borjaglez.cqrs.rabbitmq.infrastructure;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Address;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.amqp.support.converter.MessageConverter;

import com.rabbitmq.client.Channel;

public class ExtendedMessageListenerAdapter extends MessageListenerAdapter {

  private static final String HEADER_ERROR = "cqrs.error";

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

  private void sendErrorResponse(Channel channel, Message originalMessage, Exception error) {
    try {
      String errorBody =
          error.getMessage() != null ? error.getMessage() : error.getClass().getName();

      MessageProperties replyProperties = new MessageProperties();
      replyProperties.setHeader(HEADER_ERROR, true);

      String correlationId = originalMessage.getMessageProperties().getCorrelationId();
      if (correlationId != null) {
        replyProperties.setCorrelationId(correlationId);
      }

      Message errorMessage =
          MessageBuilder.withBody(errorBody.getBytes(StandardCharsets.UTF_8))
              .andProperties(replyProperties)
              .build();

      Address replyAddress = new Address(originalMessage.getMessageProperties().getReplyTo());
      channel.basicPublish(
          replyAddress.getExchangeName(),
          replyAddress.getRoutingKey(),
          null,
          errorMessage.getBody());
    } catch (Exception publishError) {
      // If we cannot send the error reply, there is nothing more we can do
    }
  }
}
