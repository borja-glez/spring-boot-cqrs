package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@CqrsMessage(service = "shop", module = "order", name = "order-placed")
public class TestOrderPlaced extends Event {

  private String orderId;

  public TestOrderPlaced(String orderId) {
    this.orderId = orderId;
  }
}
