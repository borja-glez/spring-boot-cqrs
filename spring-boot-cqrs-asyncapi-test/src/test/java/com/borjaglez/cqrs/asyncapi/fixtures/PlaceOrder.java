package com.borjaglez.cqrs.asyncapi.fixtures;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;

@Getter
@CqrsMessage(service = "orders", module = "order", name = "place-order")
public class PlaceOrder extends Command {

  private final String customerId;
  private final Money total;

  public PlaceOrder(String customerId, Money total) {
    this.customerId = customerId;
    this.total = total;
  }
}
