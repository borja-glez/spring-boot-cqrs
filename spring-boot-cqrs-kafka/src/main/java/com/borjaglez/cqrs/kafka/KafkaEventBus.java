package com.borjaglez.cqrs.kafka;

import java.util.List;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;

/**
 * {@link EventBus} that publishes events to the Kafka events topic.
 *
 * <p>{@link #publish(Event)} returns only after the broker has acknowledged the record. When the
 * send fails, the exception thrown by {@link KafkaMessagePublisher#publish(String, Object)} is
 * rethrown unchanged and the event is not delivered anywhere else, so the caller always knows
 * whether the event left the process. Local handlers receive a published event through this
 * application's own event consumer group, like any other service.
 *
 * <p>{@link #publish(List)} publishes the events one by one, in order, and stops at the first
 * failure: the events before it have been sent, the failing one and the ones after it have not.
 * There is no batching or rollback.
 *
 * <p>This bus is not wrapped by the transactional event bus, so a caller inside a transaction
 * publishes before the transaction commits. Use an outbox when publication has to be reliable and
 * consistent with the transaction.
 */
public class KafkaEventBus implements EventBus {

  private final KafkaMessagePublisher publisher;
  private final KafkaTopicNamingStrategy topicNamingStrategy;
  private final String topicName;

  public KafkaEventBus(
      KafkaMessagePublisher publisher,
      KafkaTopicNamingStrategy topicNamingStrategy,
      String topicName) {
    this.publisher = publisher;
    this.topicNamingStrategy = topicNamingStrategy;
    this.topicName = topicName;
  }

  @Override
  public void publish(Event event) {
    publisher.publish(topicNamingStrategy.topic(topicName), event);
  }

  @Override
  public void publish(List<Event> events) {
    events.forEach(this::publish);
  }
}
