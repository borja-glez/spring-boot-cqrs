package com.borjaglez.cqrs.example.rabbitmq.query;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@CqrsMessage(service = "order-service", module = "order", name = "get-order")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GetOrderQuery extends Query {

  private String orderId;
}
