package com.borjaglez.cqrs.example.multiservice.order.query;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "order-service", module = "order", name = "get-order")
public class GetOrderQuery extends Query {

  private String orderId;
}
