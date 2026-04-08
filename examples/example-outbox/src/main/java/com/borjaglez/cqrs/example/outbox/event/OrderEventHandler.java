package com.borjaglez.cqrs.example.outbox.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

@EventHandler
public class OrderEventHandler {

  private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);

  @HandleEvent
  public void onOrderCreated(OrderCreatedEvent event) {
    log.info(
        "Published order-created event from outbox for order {} and product {}",
        event.getOrderId(),
        event.getProduct());
  }
}
