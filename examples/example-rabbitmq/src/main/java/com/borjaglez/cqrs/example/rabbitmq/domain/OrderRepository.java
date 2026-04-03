package com.borjaglez.cqrs.example.rabbitmq.domain;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

  void save(Order order);

  Optional<Order> findById(String id);

  List<Order> findAll();
}
