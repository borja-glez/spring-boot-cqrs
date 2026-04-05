package com.borjaglez.cqrs.example.multiservice.inventory.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.example.multiservice.shared.event.OrderPlacedEvent;

@EventHandler
public class OrderPlacedHandler {

  private static final Logger log = LoggerFactory.getLogger(OrderPlacedHandler.class);

  @HandleEvent
  public void onOrderPlaced(OrderPlacedEvent event) {
    log.info(
        "Inventory service notified: order {} placed for {} x{}",
        event.getOrderId(),
        event.getProductId(),
        event.getQuantity());
  }
}
