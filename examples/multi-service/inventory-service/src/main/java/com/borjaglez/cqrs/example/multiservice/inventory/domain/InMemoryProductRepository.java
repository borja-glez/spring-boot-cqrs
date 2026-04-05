package com.borjaglez.cqrs.example.multiservice.inventory.domain;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class InMemoryProductRepository implements ProductRepository {

  private final ConcurrentHashMap<String, Product> store = new ConcurrentHashMap<>();

  public InMemoryProductRepository() {
    store.put("LAPTOP-001", new Product("LAPTOP-001", "Gaming Laptop", 10));
    store.put("MONITOR-001", new Product("MONITOR-001", "4K Monitor", 5));
    store.put("KEYBOARD-001", new Product("KEYBOARD-001", "Mechanical Keyboard", 50));
  }

  @Override
  public void save(Product product) {
    store.put(product.getId(), product);
  }

  @Override
  public Optional<Product> findById(String id) {
    return Optional.ofNullable(store.get(id));
  }
}
