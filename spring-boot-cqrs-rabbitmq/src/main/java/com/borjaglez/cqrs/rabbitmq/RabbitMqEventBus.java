package com.borjaglez.cqrs.rabbitmq;

import java.time.Duration;
import java.util.List;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.event.EventHandlerExecutionException;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqNamingStrategy;
import com.borjaglez.cqrs.rabbitmq.infrastructure.RabbitMqPublisher;

/**
 * {@link EventBus} that publishes events to the RabbitMQ events exchange.
 *
 * <p>Before an event is published, it passes the middlewares that declare {@link
 * DispatchPhase#OUTBOUND}, in the sending process; a middleware can stop the publication by
 * throwing.
 *
 * <p>When the send fails (for example, the broker is unreachable), the exception thrown by {@link
 * RabbitMqPublisher} is rethrown unchanged and the event is not delivered anywhere else, so the
 * caller always knows whether the event left the process. Local handlers receive a published event
 * through this application's own events queue, like any other service.
 *
 * <p>Without publisher confirms, {@link #publish(Event)} returns once the message has been written
 * to the channel, before the broker has accepted it. With a confirm timeout, it waits for the
 * broker to confirm the message and throws {@link PublishNotConfirmedException} when the broker
 * rejects it or does not confirm it in time. Confirms need a connection factory with correlated
 * publisher confirms ({@code spring.rabbitmq.publisher-confirm-type=correlated}).
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
public class RabbitMqEventBus implements EventBus {

  private final RabbitMqPublisher publisher;
  private final RabbitMqNamingStrategy rabbitNaming;
  private final MessageNamingStrategy messageNaming;
  private final String exchangeName;
  private final Duration confirmTimeout;
  private final List<BusMiddleware> middlewares;

  /** Creates a bus that publishes without waiting for publisher confirms. */
  public RabbitMqEventBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      String exchangeName) {
    this(publisher, rabbitNaming, messageNaming, exchangeName, null);
  }

  /**
   * Creates a bus that waits up to {@code confirmTimeout} for the broker to confirm each event, or
   * that does not wait for confirms when {@code confirmTimeout} is {@code null}.
   */
  public RabbitMqEventBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      String exchangeName,
      Duration confirmTimeout) {
    this(publisher, rabbitNaming, messageNaming, exchangeName, confirmTimeout, List.of());
  }

  /**
   * Creates a bus like {@link #RabbitMqEventBus(RabbitMqPublisher, RabbitMqNamingStrategy,
   * MessageNamingStrategy, String, Duration)} that also runs, before publishing, the middlewares of
   * {@code middlewares} that declare {@link DispatchPhase#OUTBOUND}, in the order of the list.
   */
  public RabbitMqEventBus(
      RabbitMqPublisher publisher,
      RabbitMqNamingStrategy rabbitNaming,
      MessageNamingStrategy messageNaming,
      String exchangeName,
      Duration confirmTimeout,
      List<BusMiddleware> middlewares) {
    this.publisher = publisher;
    this.rabbitNaming = rabbitNaming;
    this.messageNaming = messageNaming;
    this.exchangeName = exchangeName;
    this.confirmTimeout = confirmTimeout;
    this.middlewares = DispatchPhase.OUTBOUND.select(middlewares);
  }

  @Override
  public void publish(Event event) {
    try {
      new DefaultMiddlewareChain(middlewares, this::send).proceed(event);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new EventHandlerExecutionException(
          "Failed to publish event " + event.getClass().getName(), e);
    }
  }

  private Object send(Object event) {
    String exchange = rabbitNaming.exchange(exchangeName);
    String routingKey = messageNaming.eventName(event.getClass());
    if (confirmTimeout == null) {
      publisher.publish(exchange, routingKey, event, "event");
    } else {
      publisher.publishConfirmed(exchange, routingKey, event, "event", confirmTimeout);
    }
    return null;
  }

  @Override
  public void publish(List<Event> events) {
    events.forEach(this::publish);
  }
}
