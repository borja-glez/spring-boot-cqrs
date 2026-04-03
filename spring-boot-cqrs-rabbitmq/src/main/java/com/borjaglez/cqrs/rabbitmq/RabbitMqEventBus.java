package com.borjaglez.cqrs.rabbitmq;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

public class RabbitMqEventBus implements EventBus {

  private static final Logger log = LoggerFactory.getLogger(RabbitMqEventBus.class);

  private final RabbitMqPublisher publisher;
  private final RabbitMqNamingStrategy rabbitNaming;
  private final MessageNamingStrategy messageNaming;
  private final String exchangeName;
  private final EventBus fallbackEventBus;

  public RabbitMqEventBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      String exchangeName,
      EventBus fallbackEventBus) {
    this.publisher = publisher;
    this.rabbitNaming = rabbitNaming;
    this.messageNaming = messageNaming;
    this.exchangeName = exchangeName;
    this.fallbackEventBus = fallbackEventBus;
  }

  @Override
  public void publish(Event event) {
    try {
      String exchange = rabbitNaming.exchange(exchangeName);
      String routingKey = messageNaming.eventName(event.getClass());
      publisher.publish(exchange, routingKey, event, "event");
    } catch (AmqpException e) {
      log.warn(
          "Failed to publish event {} via RabbitMQ, falling back to local bus: {}",
          event.getClass().getSimpleName(),
          e.getMessage());
      fallbackEventBus.publish(event);
    }
  }

  @Override
  public void publish(List<Event> events) {
    events.forEach(this::publish);
  }
}
