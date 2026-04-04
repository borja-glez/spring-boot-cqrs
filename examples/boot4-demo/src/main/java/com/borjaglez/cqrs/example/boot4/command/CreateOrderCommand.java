package com.borjaglez.cqrs.example.boot4.command;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@CqrsMessage(service = "order-service", module = "order", name = "create-order")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderCommand extends Command {

  private String product;
  private int quantity;
}
