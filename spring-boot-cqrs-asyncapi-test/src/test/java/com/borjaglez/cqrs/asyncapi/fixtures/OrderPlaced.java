package com.borjaglez.cqrs.asyncapi.fixtures;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;

@Getter
@CqrsMessage(service = "orders", module = "order", name = "order-placed")
public class OrderPlaced extends Event {

  private final String orderId;
  private final String customerId;
  private final Money total;

  public OrderPlaced(String orderId, String customerId, Money total) {
    this.orderId = orderId;
    this.customerId = customerId;
    this.total = total;
  }
}
