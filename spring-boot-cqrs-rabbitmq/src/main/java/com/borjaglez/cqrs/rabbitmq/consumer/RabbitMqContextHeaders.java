package com.borjaglez.cqrs.rabbitmq.consumer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.amqp.core.Message;

import com.borjaglez.cqrs.context.ContextPropagationMiddleware;
import com.borjaglez.cqrs.context.MessageContext;

final class RabbitMqContextHeaders {

  private RabbitMqContextHeaders() {}

  static MessageContext extract(Message message, String headerPrefix) {
    Map<String, Object> raw = message.getMessageProperties().getHeaders();
    if (raw.isEmpty()) {
      return MessageContext.empty();
    }
    Map<String, String> headers = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      headers.put(entry.getKey(), Objects.toString(entry.getValue(), null));
    }
    return ContextPropagationMiddleware.fromHeaders(headers, headerPrefix);
  }
}
