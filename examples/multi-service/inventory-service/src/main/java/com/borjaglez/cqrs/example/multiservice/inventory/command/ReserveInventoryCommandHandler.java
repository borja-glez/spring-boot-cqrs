package com.borjaglez.cqrs.example.multiservice.inventory.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.example.multiservice.inventory.domain.Product;
import com.borjaglez.cqrs.example.multiservice.inventory.domain.ProductRepository;
import com.borjaglez.cqrs.example.multiservice.shared.command.ReserveInventoryCommand;
import com.borjaglez.cqrs.example.multiservice.shared.event.InventoryRejectedEvent;
import com.borjaglez.cqrs.example.multiservice.shared.event.InventoryReservedEvent;
import com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus;

@CommandHandler
public class ReserveInventoryCommandHandler {

  private static final Logger log = LoggerFactory.getLogger(ReserveInventoryCommandHandler.class);

  private final ProductRepository repository;
  private final RabbitMqEventBus eventBus;

  public ReserveInventoryCommandHandler(ProductRepository repository, RabbitMqEventBus eventBus) {
    this.repository = repository;
    this.eventBus = eventBus;
  }

  @HandleCommand
  public String handle(ReserveInventoryCommand command) {
    Product product =
        repository
            .findById(command.getProductId())
            .orElseThrow(
                () -> new IllegalArgumentException("Product not found: " + command.getProductId()));

    if (product.hasStock(command.getQuantity())) {
      product.reserve(command.getQuantity());
      repository.save(product);
      log.info(
          "Reserved {} units of {} for order {}",
          command.getQuantity(),
          command.getProductId(),
          command.getOrderId());
      eventBus.publish(
          new InventoryReservedEvent(
              command.getOrderId(), command.getProductId(), command.getQuantity()));
      return "RESERVED";
    } else {
      log.warn(
          "Insufficient stock for product {} (requested {}, available {})",
          command.getProductId(),
          command.getQuantity(),
          product.getStock());
      eventBus.publish(
          new InventoryRejectedEvent(
              command.getOrderId(),
              command.getProductId(),
              "Insufficient stock: requested "
                  + command.getQuantity()
                  + ", available "
                  + product.getStock()));
      return "REJECTED";
    }
  }
}
