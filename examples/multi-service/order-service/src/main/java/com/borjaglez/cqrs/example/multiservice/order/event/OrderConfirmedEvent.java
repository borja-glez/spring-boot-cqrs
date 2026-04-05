package com.borjaglez.cqrs.example.multiservice.order.event;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "order-service", module = "order", name = "order-confirmed")
public class OrderConfirmedEvent extends Event {

  private String orderId;
}
