package com.borjaglez.cqrs.example.multiservice.inventory.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.example.multiservice.inventory.command.AddProductCommand;
import com.borjaglez.cqrs.example.multiservice.shared.command.ReserveInventoryCommand;
import com.borjaglez.cqrs.example.multiservice.shared.dto.ProductStockDto;
import com.borjaglez.cqrs.example.multiservice.shared.query.GetProductStockQuery;
import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

  private final RabbitMqCommandBus commandBus;
  private final RabbitMqQueryBus queryBus;

  public InventoryController(RabbitMqCommandBus commandBus, RabbitMqQueryBus queryBus) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @PostMapping("/products")
  public ResponseEntity<String> addProduct(@RequestBody AddProductRequest request) {
    String productId =
        commandBus.dispatchAndReceive(
            new AddProductCommand(request.productId(), request.name(), request.initialStock()));
    return ResponseEntity.status(HttpStatus.CREATED).body(productId);
  }

  @GetMapping("/products/{productId}")
  public ResponseEntity<ProductStockDto> getStock(@PathVariable String productId) {
    ProductStockDto stock = queryBus.ask(new GetProductStockQuery(productId));
    return ResponseEntity.ok(stock);
  }

  @PostMapping("/reserve")
  public ResponseEntity<String> reserve(@RequestBody ReserveRequest request) {
    String result =
        commandBus.dispatchAndReceive(
            new ReserveInventoryCommand(
                request.orderId(), request.productId(), request.quantity()));
    return ResponseEntity.ok(result);
  }

  public record AddProductRequest(String productId, String name, int initialStock) {}

  public record ReserveRequest(String orderId, String productId, int quantity) {}
}
