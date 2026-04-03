package com.borjaglez.cqrs.example.rabbitmq.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Order {

  private final String id;
  private final String product;
  private final int quantity;
  private String status;

  public Order(String id, String product, int quantity) {
    this.id = id;
    this.product = product;
    this.quantity = quantity;
    this.status = "PENDING";
  }
}
