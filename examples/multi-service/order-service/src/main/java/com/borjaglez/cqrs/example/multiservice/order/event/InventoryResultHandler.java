package com.borjaglez.cqrs.example.multiservice.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.example.multiservice.order.domain.OrderRepository;
import com.borjaglez.cqrs.example.multiservice.shared.event.InventoryRejectedEvent;
import com.borjaglez.cqrs.example.multiservice.shared.event.InventoryReservedEvent;
import com.borjaglez.cqrs.rabbitmq.RabbitMqEventBus;

@EventHandler
public class InventoryResultHandler {

  private static final Logger log = LoggerFactory.getLogger(InventoryResultHandler.class);

  private final OrderRepository repository;
  private final RabbitMqEventBus eventBus;

  public InventoryResultHandler(OrderRepository repository, RabbitMqEventBus eventBus) {
    this.repository = repository;
    this.eventBus = eventBus;
  }

  @HandleEvent
  public void onReserved(InventoryReservedEvent event) {
    log.info(
        "Inventory reserved for order {}: {} units of {}",
        event.getOrderId(),
        event.getReservedQuantity(),
        event.getProductId());
    repository
        .findById(event.getOrderId())
        .ifPresent(
            order -> {
              order.confirm();
              repository.save(order);
              eventBus.publish(order.pullEvents());
            });
  }

  @HandleEvent
  public void onRejected(InventoryRejectedEvent event) {
    log.warn("Inventory rejected for order {}: {}", event.getOrderId(), event.getReason());
    repository
        .findById(event.getOrderId())
        .ifPresent(
            order -> {
              order.reject(event.getReason());
              repository.save(order);
              eventBus.publish(order.pullEvents());
            });
  }
}
