package com.borjaglez.cqrs.example.multiservice.order.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class InMemoryOrderRepository implements OrderRepository {

  private final ConcurrentHashMap<String, Order> store = new ConcurrentHashMap<>();

  @Override
  public void save(Order order) {
    store.put(order.getId(), order);
  }

  @Override
  public Optional<Order> findById(String id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Order> findAll() {
    return new ArrayList<>(store.values());
  }
}
