package com.borjaglez.cqrs.asyncapi.fixtures;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

import lombok.Getter;

@Getter
@CqrsMessage(service = "orders", module = "order", name = "find-order")
public class FindOrder extends Query {

  private final String orderId;

  public FindOrder(String orderId) {
    this.orderId = orderId;
  }
}
