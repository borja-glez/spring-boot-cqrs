package com.borjaglez.cqrs.example.outbox.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.example.outbox.command.CreateOrderCommand;
import com.borjaglez.cqrs.example.outbox.domain.OrderEntity;
import com.borjaglez.cqrs.example.outbox.domain.OrderRepository;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final CommandBus commandBus;
  private final OrderRepository orderRepository;

  public OrderController(CommandBus commandBus, OrderRepository orderRepository) {
    this.commandBus = commandBus;
    this.orderRepository = orderRepository;
  }

  @PostMapping
  public ResponseEntity<String> create(@RequestBody CreateOrderRequest request) {
    String orderId =
        commandBus.dispatchAndReceive(new CreateOrderCommand(request.product(), request.quantity()));
    return ResponseEntity.created(URI.create("/api/orders/" + orderId)).body(orderId);
  }

  @GetMapping
  public List<OrderResponse> list() {
    return orderRepository.findAll().stream().map(OrderResponse::from).toList();
  }

  public record CreateOrderRequest(String product, int quantity) {}

  public record OrderResponse(String id, String product, int quantity, String status) {

    static OrderResponse from(OrderEntity order) {
      return new OrderResponse(
          order.getId().toString(), order.getProduct(), order.getQuantity(), order.getStatus());
    }
  }
}
