package com.borjaglez.cqrs.example.rabbitmq.event;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@CqrsMessage(service = "order-service", module = "order", name = "order-created")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent extends Event {

  private String orderId;
  private String product;
  private int quantity;
}
