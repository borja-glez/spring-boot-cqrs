package com.borjaglez.cqrs.example.rabbitmq.command;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@CqrsMessage(service = "order-service", module = "order", name = "confirm-order")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmOrderCommand extends Command {

  private String orderId;
}
