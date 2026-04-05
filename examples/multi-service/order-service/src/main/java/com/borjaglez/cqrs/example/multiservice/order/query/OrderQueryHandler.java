package com.borjaglez.cqrs.example.multiservice.order.query;

import java.util.List;

import com.borjaglez.cqrs.example.multiservice.order.domain.Order;
import com.borjaglez.cqrs.example.multiservice.order.domain.OrderRepository;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class OrderQueryHandler {

  private final OrderRepository repository;

  public OrderQueryHandler(OrderRepository repository) {
    this.repository = repository;
  }

  @HandleQuery
  public Order handle(GetOrderQuery query) {
    return repository
        .findById(query.getOrderId())
        .orElseThrow(() -> new IllegalArgumentException("Order not found: " + query.getOrderId()));
  }

  @HandleQuery
  public List<Order> handleAll(GetAllOrdersQuery query) {
    return repository.findAll();
  }
}
