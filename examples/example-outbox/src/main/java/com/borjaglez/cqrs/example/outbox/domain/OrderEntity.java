package com.borjaglez.cqrs.example.outbox.domain;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
public class OrderEntity {

  @Id private UUID id;
  private String product;
  private int quantity;
  private String status;

  public OrderEntity(UUID id, String product, int quantity) {
    this.id = id;
    this.product = product;
    this.quantity = quantity;
    this.status = "CREATED";
  }
}
