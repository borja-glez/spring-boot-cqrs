package com.borjaglez.cqrs.rabbitmq.infrastructure;

public interface RabbitMqNamingStrategy {

  String exchange(String exchangeName);

  String exchangeRetry(String exchangeName);

  String exchangeDeadLetter(String exchangeName);

  String queue(String applicationName, String exchangeName);

  String queueRetry(String applicationName, String exchangeName);

  String queueDeadLetter(String applicationName, String exchangeName);
}
