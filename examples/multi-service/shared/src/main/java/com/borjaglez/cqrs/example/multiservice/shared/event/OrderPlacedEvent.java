package com.borjaglez.cqrs.example.multiservice.shared.event;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "order-service", module = "order", name = "order-placed")
public class OrderPlacedEvent extends Event {

  private String orderId;
  private String productId;
  private int quantity;
  private String customerEmail;
}
