package com.borjaglez.cqrs.example.outbox.event;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;

@Getter
@CqrsMessage(service = "example-outbox", module = "order", name = "order-created")
public class OrderCreatedEvent extends Event {

  private final String orderId;
  private final String product;
  private final int quantity;

  public OrderCreatedEvent(String orderId, String product, int quantity) {
    super();
    this.orderId = orderId;
    this.product = product;
    this.quantity = quantity;
  }
}
