package com.borjaglez.cqrs.rabbitmq.consumer;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;

public abstract class RabbitMqConsumer {

  private static final String HEADER_REDELIVERY_COUNT = "cqrs.redelivery.count";

  private final RabbitTemplate rabbitTemplate;
  private final RabbitMqNamingStrategy namingStrategy;

  protected RabbitMqConsumer(RabbitTemplate rabbitTemplate, RabbitMqNamingStrategy namingStrategy) {
    this.rabbitTemplate = rabbitTemplate;
    this.namingStrategy = namingStrategy;
  }

  protected void handleConsumptionError(Message message, String exchangeName, String appName) {
    int redeliveryCount = getRedeliveryCount(message);

    if (redeliveryCount < getMaxRetries()) {
      redeliveryCount++;
      message.getMessageProperties().setHeader(HEADER_REDELIVERY_COUNT, redeliveryCount);

      String retryExchange = namingStrategy.exchangeRetry(exchangeName);
      rabbitTemplate.send(retryExchange, "#", message);
    } else {
      String deadLetterExchange = namingStrategy.exchangeDeadLetter(exchangeName);
      rabbitTemplate.send(deadLetterExchange, "#", message);
    }
  }

  protected int getMaxRetries() {
    return 3;
  }

  private int getRedeliveryCount(Message message) {
    Object header = message.getMessageProperties().getHeader(HEADER_REDELIVERY_COUNT);
    if (header instanceof Integer count) {
      return count;
    }
    return 0;
  }
}
