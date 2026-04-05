package com.borjaglez.cqrs.example.multiservice.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

@EventHandler
public class OrderStatusEventHandler {

  private static final Logger log = LoggerFactory.getLogger(OrderStatusEventHandler.class);

  @HandleEvent
  public void onConfirmed(OrderConfirmedEvent event) {
    log.info("Order {} has been confirmed", event.getOrderId());
  }

  @HandleEvent
  public void onRejected(OrderRejectedEvent event) {
    log.warn("Order {} has been rejected: {}", event.getOrderId(), event.getReason());
  }
}
