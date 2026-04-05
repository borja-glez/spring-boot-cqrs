package com.borjaglez.cqrs.example.multiservice.order.domain;

import com.borjaglez.cqrs.event.AggregateRoot;
import com.borjaglez.cqrs.example.multiservice.order.event.OrderConfirmedEvent;
import com.borjaglez.cqrs.example.multiservice.order.event.OrderRejectedEvent;
import com.borjaglez.cqrs.example.multiservice.shared.event.OrderPlacedEvent;

import lombok.Getter;

@Getter
public class Order extends AggregateRoot {

  private final String id;
  private final String productId;
  private final int quantity;
  private final String customerEmail;
  private String status;

  public Order(String id, String productId, int quantity, String customerEmail) {
    this.id = id;
    this.productId = productId;
    this.quantity = quantity;
    this.customerEmail = customerEmail;
    this.status = "PENDING";
    record(new OrderPlacedEvent(id, productId, quantity, customerEmail));
  }

  public void confirm() {
    this.status = "CONFIRMED";
    record(new OrderConfirmedEvent(id));
  }

  public void reject(String reason) {
    this.status = "REJECTED";
    record(new OrderRejectedEvent(id, reason));
  }
}
