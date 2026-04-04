package com.borjaglez.cqrs.example.boot4.event;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@CqrsMessage(service = "order-service", module = "order", name = "order-confirmed")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmedEvent extends Event {

  private String orderId;
}
