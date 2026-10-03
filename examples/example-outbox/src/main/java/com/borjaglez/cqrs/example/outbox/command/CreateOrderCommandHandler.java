package com.borjaglez.cqrs.example.outbox.command;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.example.outbox.domain.OrderEntity;
import com.borjaglez.cqrs.example.outbox.domain.OrderRepository;
import com.borjaglez.cqrs.example.outbox.event.OrderCreatedEvent;
import com.borjaglez.cqrs.jdbc.outbox.OutboxEventBus;

@CommandHandler
public class CreateOrderCommandHandler {

  private final OrderRepository orderRepository;
  private final OutboxEventBus outbox;

  public CreateOrderCommandHandler(OrderRepository orderRepository, OutboxEventBus outbox) {
    this.orderRepository = orderRepository;
    this.outbox = outbox;
  }

  /** The order and its event commit together; the relay sends the event to Kafka afterwards. */
  @HandleCommand
  @Transactional
  public String handle(CreateOrderCommand command) {
    UUID orderId = UUID.randomUUID();
    orderRepository.save(new OrderEntity(orderId, command.getProduct(), command.getQuantity()));
    outbox.publish(
        new OrderCreatedEvent(orderId.toString(), command.getProduct(), command.getQuantity()));
    return orderId.toString();
  }
}
