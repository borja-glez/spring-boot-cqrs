package com.borjaglez.cqrs.example.boot4.command;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.example.boot4.domain.Order;
import com.borjaglez.cqrs.example.boot4.domain.OrderRepository;
import com.borjaglez.cqrs.example.boot4.event.OrderConfirmedEvent;

@CommandHandler
public class ConfirmOrderCommandHandler {

  private final OrderRepository orderRepository;
  private final EventBus eventBus;

  public ConfirmOrderCommandHandler(OrderRepository orderRepository, EventBus eventBus) {
    this.orderRepository = orderRepository;
    this.eventBus = eventBus;
  }

  @HandleCommand
  public void handle(ConfirmOrderCommand command) {
    Order order =
        orderRepository
            .findById(command.getOrderId())
            .orElseThrow(
                () -> new IllegalArgumentException("Order not found: " + command.getOrderId()));
    order.setStatus("CONFIRMED");
    orderRepository.save(order);

    eventBus.publish(new OrderConfirmedEvent(order.getId()));
  }
}
