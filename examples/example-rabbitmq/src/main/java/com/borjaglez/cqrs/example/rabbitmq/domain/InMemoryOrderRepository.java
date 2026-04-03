package com.borjaglez.cqrs.example.rabbitmq.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

  private final Map<String, Order> orders = new ConcurrentHashMap<>();

  @Override
  public void save(Order order) {
    orders.put(order.getId(), order);
  }

  @Override
  public Optional<Order> findById(String id) {
    return Optional.ofNullable(orders.get(id));
  }

  @Override
  public List<Order> findAll() {
    return new ArrayList<>(orders.values());
  }
}
