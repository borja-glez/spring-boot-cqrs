package com.borjaglez.cqrs.asyncapi.fixtures;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;

/** {@link PlaceOrder} after a field was added in Java but not in the contract. */
@Getter
@CqrsMessage(service = "orders", module = "order", name = "place-order")
public class PlaceOrderWithNote extends Command {

  private final String customerId;
  private final Money total;
  private final String note;

  public PlaceOrderWithNote(String customerId, Money total, String note) {
    this.customerId = customerId;
    this.total = total;
    this.note = note;
  }
}
