package com.borjaglez.cqrs.kafka;

import java.util.List;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.EventHandlerExecutionException;
import com.borjaglez.cqrs.kafka.infrastructure.KafkaTopicNamingStrategy;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.middleware.DispatchPhase;

/**
 * {@link EventBus} that publishes events to the Kafka events topic.
 *
 * <p>Before an event is published, it passes the middlewares that declare {@link
 * DispatchPhase#OUTBOUND}, in the sending process; a middleware can stop the publication by
 * throwing.
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
 * publishes before the transaction commits. When publication has to be reliable and consistent with
 * the transaction, publish through {@code OutboxEventBus} of spring-boot-cqrs-jdbc, which stores
 * the event in the transaction and relays it through this bus after commit.
 */
public class KafkaEventBus implements EventBus {

  private final KafkaMessagePublisher publisher;
  private final KafkaTopicNamingStrategy topicNamingStrategy;
  private final String topicName;
  private final List<BusMiddleware> middlewares;

  /** Creates a bus that runs no middleware before publishing. */
  public KafkaEventBus(
      KafkaMessagePublisher publisher,
      KafkaTopicNamingStrategy topicNamingStrategy,
      String topicName) {
    this(publisher, topicNamingStrategy, topicName, List.of());
  }

  /**
   * Creates a bus that runs, before publishing, the middlewares of {@code middlewares} that declare
   * {@link DispatchPhase#OUTBOUND}, in the order of the list.
   */
  public KafkaEventBus(
      KafkaMessagePublisher publisher,
      KafkaTopicNamingStrategy topicNamingStrategy,
      String topicName,
      List<BusMiddleware> middlewares) {
    this.publisher = publisher;
    this.topicNamingStrategy = topicNamingStrategy;
    this.topicName = topicName;
    this.middlewares = DispatchPhase.OUTBOUND.select(middlewares);
  }

  @Override
  public void publish(Event event) {
    try {
      new DefaultMiddlewareChain(
              middlewares,
              message -> {
                publisher.publish(topicNamingStrategy.topic(topicName), message);
                return null;
              })
          .proceed(event);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new EventHandlerExecutionException(
          "Failed to publish event " + event.getClass().getName(), e);
    }
  }

  @Override
  public void publish(List<Event> events) {
    events.forEach(this::publish);
  }
}
