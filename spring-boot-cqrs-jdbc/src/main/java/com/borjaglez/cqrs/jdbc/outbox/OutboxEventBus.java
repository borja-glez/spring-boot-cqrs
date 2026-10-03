package com.borjaglez.cqrs.jdbc.outbox;

import java.util.List;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.naming.MessageNamingStrategy;
import com.borjaglez.cqrs.serialization.MessageSerializer;

/**
 * {@link EventBus} that stores each event as an outbox row in the current transaction. Nothing is
 * sent here: the row commits or rolls back with the caller's changes, and the outbox relay
 * publishes it after commit through the transport's event bus. {@link #publish(Event)} fails
 * without an active transaction, because the event would otherwise not be tied to any change.
 *
 * <p>This bus is not {@code @Primary}: inject it where an event must leave the process if and only
 * if the transaction commits. Middlewares do not run here; the outbound ones run when the relay
 * publishes.
 */
public class OutboxEventBus implements EventBus {

  private final OutboxStore store;
  private final MessageSerializer serializer;
  private final MessageNamingStrategy naming;
  private final OutboxContextCodec contextCodec;

  public OutboxEventBus(
      OutboxStore store,
      MessageSerializer serializer,
      MessageNamingStrategy naming,
      OutboxContextCodec contextCodec) {
    this.store = store;
    this.serializer = serializer;
    this.naming = naming;
    this.contextCodec = contextCodec;
  }

  @Override
  public void publish(Event event) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "OutboxEventBus needs an active transaction to store "
              + event.getClass().getName()
              + "; publish it from a @Transactional method so it commits with your changes");
    }
    store.insert(
        event.getEventId(),
        naming.eventName(event.getClass()),
        event.getClass().getName(),
        serializer.serialize(event),
        contextCodec.capture());
  }

  @Override
  public void publish(List<Event> events) {
    events.forEach(this::publish);
  }
}
