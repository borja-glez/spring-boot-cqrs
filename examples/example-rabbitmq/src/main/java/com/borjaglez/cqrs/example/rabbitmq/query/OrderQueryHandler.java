package com.borjaglez.cqrs.example.rabbitmq.query;

import java.util.List;

import com.borjaglez.cqrs.example.rabbitmq.domain.Order;
import com.borjaglez.cqrs.example.rabbitmq.domain.OrderRepository;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class OrderQueryHandler {

  private final OrderRepository orderRepository;

  public OrderQueryHandler(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @HandleQuery
  public Order handle(GetOrderQuery query) {
    return orderRepository
        .findById(query.getOrderId())
        .orElseThrow(() -> new IllegalArgumentException("Order not found: " + query.getOrderId()));
  }

  @HandleQuery
  public List<Order> handleAll(GetAllOrdersQuery query) {
    return orderRepository.findAll();
  }
}
