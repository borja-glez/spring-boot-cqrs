package com.borjaglez.cqrs.example.multiservice.order.controller;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.example.multiservice.order.command.PlaceOrderCommand;
import com.borjaglez.cqrs.example.multiservice.order.domain.Order;
import com.borjaglez.cqrs.example.multiservice.order.query.GetAllOrdersQuery;
import com.borjaglez.cqrs.example.multiservice.order.query.GetOrderQuery;
import com.borjaglez.cqrs.example.multiservice.shared.command.ReserveInventoryCommand;
import com.borjaglez.cqrs.example.multiservice.shared.dto.ProductStockDto;
import com.borjaglez.cqrs.example.multiservice.shared.query.GetProductStockQuery;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.RabbitMqQueryBus;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  // Local Spring bus with middleware pipeline (validation, logging, observability)
  private final CommandBus localCommandBus;
  private final QueryBus localQueryBus;

  // RabbitMQ bus for cross-service communication
  private final RabbitMqCommandBus remoteCommandBus;
  private final RabbitMqQueryBus remoteQueryBus;

  public OrderController(
      CommandBus localCommandBus,
      QueryBus localQueryBus,
      RabbitMqCommandBus remoteCommandBus,
      RabbitMqQueryBus remoteQueryBus) {
    this.localCommandBus = localCommandBus;
    this.localQueryBus = localQueryBus;
    this.remoteCommandBus = remoteCommandBus;
    this.remoteQueryBus = remoteQueryBus;
  }

  @PostMapping
  public ResponseEntity<String> placeOrder(@RequestBody PlaceOrderRequest request) {
    // Local command: passes through middleware (validation + logging + observability)
    String orderId =
        localCommandBus.dispatchAndReceive(
            new PlaceOrderCommand(
                request.productId(), request.quantity(), request.customerEmail()));
    return ResponseEntity.status(HttpStatus.CREATED).body(orderId);
  }

  @GetMapping("/{id}")
  public ResponseEntity<Order> getOrder(@PathVariable String id) {
    Order order = localQueryBus.ask(new GetOrderQuery(id));
    return ResponseEntity.ok(order);
  }

  @GetMapping
  public ResponseEntity<List<Order>> getAllOrders() {
    List<Order> orders = localQueryBus.ask(new GetAllOrdersQuery());
    return ResponseEntity.ok(orders);
  }

  @GetMapping("/stock/{productId}")
  public ResponseEntity<ProductStockDto> checkStock(@PathVariable String productId) {
    // Cross-service query: sent via RabbitMQ RPC to Inventory Service
    ProductStockDto stock = remoteQueryBus.ask(new GetProductStockQuery(productId));
    return ResponseEntity.ok(stock);
  }

  @PostMapping("/{id}/reserve")
  public ResponseEntity<String> reserveInventory(
      @PathVariable String id, @RequestBody ReserveRequest request) {
    // Cross-service command with synchronous response (dispatchAndReceive via RabbitMQ RPC)
    String result =
        remoteCommandBus.dispatchAndReceive(
            new ReserveInventoryCommand(id, request.productId(), request.quantity()));
    return ResponseEntity.ok(result);
  }

  @PostMapping("/{id}/reserve-and-wait")
  public ResponseEntity<Void> reserveInventoryAndWait(
      @PathVariable String id, @RequestBody ReserveRequest request) {
    // Cross-service command: waits for completion but ignores return value (dispatchAndWait via
    // RabbitMQ RPC)
    remoteCommandBus.dispatchAndWait(
        new ReserveInventoryCommand(id, request.productId(), request.quantity()));
    return ResponseEntity.ok().build();
  }

  @GetMapping("/typed")
  public ResponseEntity<List<Order>> getAllOrdersTyped() {
    // Typed query: ParameterizedTypeReference preserves generic type for deserialization
    List<Order> orders =
        localQueryBus.ask(
            new GetAllOrdersQuery(), new ParameterizedTypeReference<List<Order>>() {});
    return ResponseEntity.ok(orders);
  }

  @GetMapping("/stock/{productId}/typed")
  public ResponseEntity<ProductStockDto> checkStockTyped(@PathVariable String productId) {
    // Cross-service typed query: ParameterizedTypeReference ensures correct deserialization over
    // RabbitMQ
    ProductStockDto stock =
        remoteQueryBus.ask(
            new GetProductStockQuery(productId),
            new ParameterizedTypeReference<ProductStockDto>() {});
    return ResponseEntity.ok(stock);
  }

  @PostMapping("/{id}/reserve/typed")
  public ResponseEntity<String> reserveInventoryTyped(
      @PathVariable String id, @RequestBody ReserveRequest request) {
    // Cross-service typed command: ParameterizedTypeReference ensures correct deserialization over
    // RabbitMQ
    String result =
        remoteCommandBus.dispatchAndReceive(
            new ReserveInventoryCommand(id, request.productId(), request.quantity()),
            new ParameterizedTypeReference<String>() {});
    return ResponseEntity.ok(result);
  }

  public record PlaceOrderRequest(String productId, int quantity, String customerEmail) {}

  public record ReserveRequest(String productId, int quantity) {}
}
