package com.borjaglez.cqrs.example.multiservice.inventory.domain;

import java.util.Optional;

public interface ProductRepository {

  void save(Product product);

  Optional<Product> findById(String id);
}
