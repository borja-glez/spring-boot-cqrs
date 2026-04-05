package com.borjaglez.cqrs.example.multiservice.shared.query;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "inventory-service", module = "inventory", name = "get-product-stock")
public class GetProductStockQuery extends Query {

  private String productId;
}
