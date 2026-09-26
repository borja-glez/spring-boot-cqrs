package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

/** Writes any payload as its {@code toString()}, for tests that do not read messages back. */
public class PlainTextMessageConverter implements MessageConverter {

  @Override
  public Message toMessage(Object object, MessageProperties messageProperties) {
    return new Message(object.toString().getBytes(StandardCharsets.UTF_8), messageProperties);
  }

  @Override
  public Object fromMessage(Message message) {
    return new String(message.getBody(), StandardCharsets.UTF_8);
  }
}
