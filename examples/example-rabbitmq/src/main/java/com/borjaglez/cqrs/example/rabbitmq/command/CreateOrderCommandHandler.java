package com.borjaglez.cqrs.example.rabbitmq.command;

import java.util.UUID;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.example.rabbitmq.domain.Order;
import com.borjaglez.cqrs.example.rabbitmq.domain.OrderRepository;
import com.borjaglez.cqrs.example.rabbitmq.event.OrderCreatedEvent;

@CommandHandler
public class CreateOrderCommandHandler {

  private final OrderRepository orderRepository;
  private final EventBus eventBus;

  public CreateOrderCommandHandler(OrderRepository orderRepository, EventBus eventBus) {
    this.orderRepository = orderRepository;
    this.eventBus = eventBus;
  }

  @HandleCommand
  public String handle(CreateOrderCommand command) {
    String orderId = UUID.randomUUID().toString();
    Order order = new Order(orderId, command.getProduct(), command.getQuantity());
    orderRepository.save(order);

    eventBus.publish(new OrderCreatedEvent(orderId, command.getProduct(), command.getQuantity()));

    return orderId;
  }
}
