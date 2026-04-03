package com.borjaglez.cqrs.rabbitmq.infrastructure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

public class RabbitMqBusDeclarationBuilder {

  private final RabbitMqNamingStrategy namingStrategy;

  public RabbitMqBusDeclarationBuilder(RabbitMqNamingStrategy namingStrategy) {
    this.namingStrategy = namingStrategy;
  }

  public Declarables buildWithRetryAndDeadLetter(
      String appName, String exchangeName, List<String> routingKeys, long retryTtl) {

    List<Declarable> declarables = new ArrayList<>();

    // Main exchange and queue
    TopicExchange mainExchange = new TopicExchange(namingStrategy.exchange(exchangeName));
    Queue mainQueue = QueueBuilder.durable(namingStrategy.queue(appName, exchangeName)).build();

    declarables.add(mainExchange);
    declarables.add(mainQueue);

    for (String routingKey : routingKeys) {
      Binding binding = BindingBuilder.bind(mainQueue).to(mainExchange).with(routingKey);
      declarables.add(binding);
    }

    // Retry exchange and queue (with TTL and DLX pointing back to main exchange)
    TopicExchange retryExchange = new TopicExchange(namingStrategy.exchangeRetry(exchangeName));
    Queue retryQueue =
        QueueBuilder.durable(namingStrategy.queueRetry(appName, exchangeName))
            .ttl((int) retryTtl)
            .deadLetterExchange(namingStrategy.exchange(exchangeName))
            .build();

    declarables.add(retryExchange);
    declarables.add(retryQueue);

    Binding retryBinding = BindingBuilder.bind(retryQueue).to(retryExchange).with("#");
    declarables.add(retryBinding);

    // Dead-letter exchange and queue
    TopicExchange deadLetterExchange =
        new TopicExchange(namingStrategy.exchangeDeadLetter(exchangeName));
    Queue deadLetterQueue =
        QueueBuilder.durable(namingStrategy.queueDeadLetter(appName, exchangeName)).build();

    declarables.add(deadLetterExchange);
    declarables.add(deadLetterQueue);

    Binding deadLetterBinding =
        BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("#");
    declarables.add(deadLetterBinding);

    return new Declarables(declarables);
  }

  public Declarables buildSimple(String appName, String exchangeName, List<String> routingKeys) {

    List<Declarable> declarables = new ArrayList<>();

    TopicExchange exchange = new TopicExchange(namingStrategy.exchange(exchangeName));
    Queue queue = QueueBuilder.durable(namingStrategy.queue(appName, exchangeName)).build();

    declarables.add(exchange);
    declarables.add(queue);

    for (String routingKey : routingKeys) {
      Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
      declarables.add(binding);
    }

    return new Declarables(declarables);
  }
}
