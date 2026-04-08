package com.borjaglez.cqrs.example.outbox.command;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;

@Getter
@CqrsMessage(service = "example-outbox", module = "order", name = "create-order")
public class CreateOrderCommand extends Command {

  private final String product;
  private final int quantity;

  public CreateOrderCommand(String product, int quantity) {
    super();
    this.product = product;
    this.quantity = quantity;
  }
}
