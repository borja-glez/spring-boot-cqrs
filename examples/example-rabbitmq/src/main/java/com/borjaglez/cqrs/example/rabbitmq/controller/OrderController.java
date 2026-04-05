package com.borjaglez.cqrs.example.rabbitmq.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.example.rabbitmq.command.ConfirmOrderCommand;
import com.borjaglez.cqrs.example.rabbitmq.command.CreateOrderCommand;
import com.borjaglez.cqrs.example.rabbitmq.domain.Order;
import com.borjaglez.cqrs.example.rabbitmq.query.GetAllOrdersQuery;
import com.borjaglez.cqrs.example.rabbitmq.query.GetOrderQuery;
import com.borjaglez.cqrs.query.QueryBus;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final CommandBus commandBus;
  private final QueryBus queryBus;

  public OrderController(CommandBus commandBus, QueryBus queryBus) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @PostMapping
  public ResponseEntity<String> createOrder(@RequestBody CreateOrderRequest request) {
    String orderId =
        commandBus.dispatchAndReceive(
            new CreateOrderCommand(request.product(), request.quantity()));
    return ResponseEntity.status(HttpStatus.CREATED).body(orderId);
  }

  @PutMapping("/{id}/confirm")
  public ResponseEntity<Void> confirmOrder(@PathVariable String id) {
    commandBus.dispatch(new ConfirmOrderCommand(id));
    return ResponseEntity.ok().build();
  }

  @PutMapping("/{id}/confirm-and-wait")
  public ResponseEntity<Void> confirmOrderAndWait(@PathVariable String id) {
    commandBus.dispatchAndWait(new ConfirmOrderCommand(id));
    return ResponseEntity.ok().build();
  }

  @GetMapping("/{id}")
  public ResponseEntity<Order> getOrder(@PathVariable String id) {
    Order order = queryBus.ask(new GetOrderQuery(id));
    return ResponseEntity.ok(order);
  }

  @GetMapping
  public ResponseEntity<List<Order>> getAllOrders() {
    List<Order> orders = queryBus.ask(new GetAllOrdersQuery());
    return ResponseEntity.ok(orders);
  }

  public record CreateOrderRequest(String product, int quantity) {}
}
