package com.borjaglez.cqrs.example.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.context.MessageContext;
import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.example.boot4.event.OrderCreatedEvent;
import com.borjaglez.cqrs.jdbc.outbox.OutboxContextCodec;
import com.borjaglez.cqrs.jdbc.outbox.OutboxEventBus;
import com.borjaglez.cqrs.jdbc.outbox.OutboxEventTypeResolver;
import com.borjaglez.cqrs.jdbc.outbox.OutboxRelay;
import com.borjaglez.cqrs.jdbc.outbox.OutboxStore;
import com.borjaglez.cqrs.serialization.MessageSerializer;

/**
 * On Spring Boot 4 (Jackson 3), the outbox stores an event in the caller's transaction, keeps the
 * local bus primary, and a relay reads it back with its context.
 */
@SpringBootTest(properties = {"cqrs.outbox.enabled=true", "cqrs.outbox.relay.enabled=false"})
class JdbcOutboxTest {

  @Autowired private OutboxEventBus outbox;
  @Autowired private EventBus eventBus;
  @Autowired private OutboxStore store;
  @Autowired private OutboxEventTypeResolver resolver;
  @Autowired private OutboxContextCodec codec;
  @Autowired private MessageSerializer serializer;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void theLocalEventBusStaysPrimary() {
    assertThat(eventBus).isNotInstanceOf(OutboxEventBus.class);
  }

  @Test
  void anEventStoredInATransactionIsRelayedWithItsContext() {
    OrderCreatedEvent event = new OrderCreatedEvent("order-9", "book", 1);
    try (MessageContext.Scope ignored =
        MessageContext.scope(
            MessageContext.empty().with(MessageContext.CORRELATION_ID_KEY, "corr-boot4"))) {
      new TransactionTemplate(transactionManager).executeWithoutResult(s -> outbox.publish(event));
    }
    List<Event> relayed = new CopyOnWriteArrayList<>();
    List<String> correlations = new CopyOnWriteArrayList<>();
    EventBus target =
        new EventBus() {
          @Override
          public void publish(Event published) {
            relayed.add(published);
            correlations.add(MessageContext.current().correlationId());
          }

          @Override
          public void publish(List<Event> events) {
            events.forEach(this::publish);
          }
        };

    new OutboxRelay(store, resolver, serializer, codec, target, transactionManager, 10, 3)
        .relayBatch();

    assertThat(relayed).extracting(Event::getEventId).containsExactly(event.getEventId());
    assertThat(((OrderCreatedEvent) relayed.get(0)).getOrderId()).isEqualTo("order-9");
    assertThat(correlations).containsExactly("corr-boot4");
  }
}
