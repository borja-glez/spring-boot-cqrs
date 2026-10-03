package com.borjaglez.cqrs.example.outbox.event;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@CqrsMessage(service = "example-outbox", module = "order", name = "order-created")
public class OrderCreatedEvent extends Event {

  private String orderId;
  private String product;
  private int quantity;

  public OrderCreatedEvent(String orderId, String product, int quantity) {
    this.orderId = orderId;
    this.product = product;
    this.quantity = quantity;
  }
}
