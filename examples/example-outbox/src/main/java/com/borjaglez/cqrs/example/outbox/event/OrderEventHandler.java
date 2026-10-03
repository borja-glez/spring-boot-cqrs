package com.borjaglez.cqrs.example.outbox.event;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

/** Receives order-created events back from Kafka, after the relay published them. */
@EventHandler
public class OrderEventHandler {

  private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);

  private final Set<String> received = ConcurrentHashMap.newKeySet();

  @HandleEvent
  public void onOrderCreated(OrderCreatedEvent event) {
    received.add(event.getOrderId());
    log.info(
        "Received order-created from Kafka for order {} and product {}",
        event.getOrderId(),
        event.getProduct());
  }

  public Set<String> received() {
    return received;
  }
}
