package com.borjaglez.cqrs.example.boot4.event;

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
        "Order created: id={}, product={}, quantity={}",
        event.getOrderId(),
        event.getProduct(),
        event.getQuantity());
  }

  @HandleEvent
  public void onOrderConfirmed(OrderConfirmedEvent event) {
    log.info("Order confirmed: id={}", event.getOrderId());
  }
}
