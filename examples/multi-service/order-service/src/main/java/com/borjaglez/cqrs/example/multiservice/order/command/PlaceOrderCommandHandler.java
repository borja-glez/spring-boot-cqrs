package com.borjaglez.cqrs.example.multiservice.order.command;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.example.multiservice.order.domain.Order;
import com.borjaglez.cqrs.example.multiservice.order.domain.OrderRepository;
import com.borjaglez.cqrs.example.multiservice.shared.command.ReserveInventoryCommand;
import com.borjaglez.cqrs.rabbitmq.RabbitMqCommandBus;
import com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus;

@CommandHandler
public class PlaceOrderCommandHandler {

  private static final Logger log = LoggerFactory.getLogger(PlaceOrderCommandHandler.class);

  private final OrderRepository repository;
  private final RabbitMqEventBus eventBus;
  private final RabbitMqCommandBus commandBus;

  public PlaceOrderCommandHandler(
      OrderRepository repository, RabbitMqEventBus eventBus, RabbitMqCommandBus commandBus) {
    this.repository = repository;
    this.eventBus = eventBus;
    this.commandBus = commandBus;
  }

  @HandleCommand
  public String handle(PlaceOrderCommand command) {
    String orderId = UUID.randomUUID().toString();
    Order order =
        new Order(
            orderId, command.getProductId(), command.getQuantity(), command.getCustomerEmail());
    repository.save(order);

    // Publish OrderPlacedEvent cross-service (Inventory + Notification receive it)
    eventBus.publish(order.pullEvents());

    // Send cross-service command to Inventory Service to reserve stock
    // The command travels via RabbitMQ to Inventory Service, which processes it
    // and publishes InventoryReservedEvent/InventoryRejectedEvent back via RabbitMQ
    commandBus.dispatch(
        new ReserveInventoryCommand(orderId, command.getProductId(), command.getQuantity()));
    log.info("Inventory reservation requested for order {}", orderId);

    return orderId;
  }
}
