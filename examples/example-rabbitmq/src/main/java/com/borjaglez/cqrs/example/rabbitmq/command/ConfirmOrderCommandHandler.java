package com.borjaglez.cqrs.example.rabbitmq.command;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.example.rabbitmq.domain.Order;
import com.borjaglez.cqrs.example.rabbitmq.domain.OrderRepository;
import com.borjaglez.cqrs.example.rabbitmq.event.OrderConfirmedEvent;

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
