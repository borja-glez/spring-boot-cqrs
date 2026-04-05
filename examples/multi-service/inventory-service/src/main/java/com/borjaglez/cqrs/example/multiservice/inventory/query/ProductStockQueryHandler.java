package com.borjaglez.cqrs.example.multiservice.inventory.query;

import com.borjaglez.cqrs.example.multiservice.inventory.domain.Product;
import com.borjaglez.cqrs.example.multiservice.inventory.domain.ProductRepository;
import com.borjaglez.cqrs.example.multiservice.shared.dto.ProductStockDto;
import com.borjaglez.cqrs.example.multiservice.shared.query.GetProductStockQuery;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class ProductStockQueryHandler {

  private final ProductRepository repository;

  public ProductStockQueryHandler(ProductRepository repository) {
    this.repository = repository;
  }

  @HandleQuery
  public ProductStockDto handle(GetProductStockQuery query) {
    Product product =
        repository
            .findById(query.getProductId())
            .orElseThrow(
                () -> new IllegalArgumentException("Product not found: " + query.getProductId()));
    return new ProductStockDto(product.getId(), product.getName(), product.getStock());
  }
}
