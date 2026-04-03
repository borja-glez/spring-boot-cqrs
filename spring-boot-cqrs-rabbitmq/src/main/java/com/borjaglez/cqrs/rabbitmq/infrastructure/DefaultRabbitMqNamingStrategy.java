package com.borjaglez.cqrs.rabbitmq.infrastructure;

public class DefaultRabbitMqNamingStrategy implements RabbitMqNamingStrategy {

  private final String prefix;

  public DefaultRabbitMqNamingStrategy(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public String exchange(String exchangeName) {
    return prefix + "." + exchangeName;
  }

  @Override
  public String exchangeRetry(String exchangeName) {
    return prefix + "." + exchangeName + ".retry";
  }

  @Override
  public String exchangeDeadLetter(String exchangeName) {
    return prefix + "." + exchangeName + ".dead_letter";
  }

  @Override
  public String queue(String applicationName, String exchangeName) {
    return prefix + "." + applicationName + "." + exchangeName;
  }

  @Override
  public String queueRetry(String applicationName, String exchangeName) {
    return prefix + "." + applicationName + "." + exchangeName + ".retry";
  }

  @Override
  public String queueDeadLetter(String applicationName, String exchangeName) {
    return prefix + "." + applicationName + "." + exchangeName + ".dead_letter";
  }
}
