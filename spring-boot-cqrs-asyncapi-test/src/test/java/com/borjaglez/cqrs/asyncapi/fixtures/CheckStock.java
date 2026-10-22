package com.borjaglez.cqrs.asyncapi.fixtures;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

import lombok.Getter;

@Getter
@CqrsMessage(service = "inventory", module = "stock", name = "check-stock")
public class CheckStock extends Query {

  private final String sku;

  public CheckStock(String sku) {
    this.sku = sku;
  }
}
