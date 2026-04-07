package com.borjaglez.cqrs.kafka;

import java.util.List;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;

public class KafkaEventBus implements EventBus {

  private final KafkaMessagePublisher publisher;
  private final KafkaTopicNamingStrategy topicNamingStrategy;
  private final String topicName;
  private final EventBus fallbackEventBus;

  public KafkaEventBus(
      KafkaMessagePublisher publisher,
      KafkaTopicNamingStrategy topicNamingStrategy,
      String topicName,
      EventBus fallbackEventBus) {
    this.publisher = publisher;
    this.topicNamingStrategy = topicNamingStrategy;
    this.topicName = topicName;
    this.fallbackEventBus = fallbackEventBus;
  }

  @Override
  public void publish(Event event) {
    try {
      publisher.publish(topicNamingStrategy.topic(topicName), event);
    } catch (RuntimeException e) {
      fallbackEventBus.publish(event);
    }
  }

  @Override
  public void publish(List<Event> events) {
    events.forEach(this::publish);
  }
}
