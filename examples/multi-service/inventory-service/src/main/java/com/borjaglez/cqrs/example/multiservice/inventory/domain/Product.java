package com.borjaglez.cqrs.example.multiservice.inventory.domain;

import lombok.Getter;

@Getter
public class Product {

  private final String id;
  private final String name;
  private int stock;

  public Product(String id, String name, int stock) {
    this.id = id;
    this.name = name;
    this.stock = stock;
  }

  public boolean hasStock(int quantity) {
    return stock >= quantity;
  }

  public void reserve(int quantity) {
    if (!hasStock(quantity)) {
      throw new IllegalStateException("Insufficient stock for product " + id);
    }
    stock -= quantity;
  }
}
