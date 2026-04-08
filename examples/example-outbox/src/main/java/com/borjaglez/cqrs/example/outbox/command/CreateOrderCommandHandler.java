package com.borjaglez.cqrs.example.outbox.command;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.example.outbox.domain.OrderEntity;
import com.borjaglez.cqrs.example.outbox.domain.OrderRepository;
import com.borjaglez.cqrs.example.outbox.event.OrderCreatedEvent;
import com.borjaglez.cqrs.example.outbox.outbox.OutboxEventFactory;
import com.borjaglez.cqrs.example.outbox.outbox.OutboxEventRepository;

@CommandHandler
public class CreateOrderCommandHandler {

  private final OrderRepository orderRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final OutboxEventFactory outboxEventFactory;

  public CreateOrderCommandHandler(
      OrderRepository orderRepository,
      OutboxEventRepository outboxEventRepository,
      OutboxEventFactory outboxEventFactory) {
    this.orderRepository = orderRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.outboxEventFactory = outboxEventFactory;
  }

  @HandleCommand
  @Transactional
  public String handle(CreateOrderCommand command) {
    UUID orderId = UUID.randomUUID();
    OrderEntity order = new OrderEntity(orderId, command.getProduct(), command.getQuantity());
    orderRepository.save(order);

    OrderCreatedEvent event =
        new OrderCreatedEvent(orderId.toString(), command.getProduct(), command.getQuantity());
    outboxEventRepository.save(outboxEventFactory.create(orderId.toString(), "Order", event));
    return orderId.toString();
  }
}
