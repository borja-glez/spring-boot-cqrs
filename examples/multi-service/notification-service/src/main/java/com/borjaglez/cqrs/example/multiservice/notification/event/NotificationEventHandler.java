package com.borjaglez.cqrs.example.multiservice.notification.event;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.example.multiservice.notification.domain.Notification;
import com.borjaglez.cqrs.example.multiservice.notification.domain.NotificationRepository;
import com.borjaglez.cqrs.example.multiservice.shared.event.InventoryRejectedEvent;
import com.borjaglez.cqrs.example.multiservice.shared.event.InventoryReservedEvent;
import com.borjaglez.cqrs.example.multiservice.shared.event.OrderPlacedEvent;

@EventHandler
public class NotificationEventHandler {

  private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

  private final NotificationRepository repository;

  public NotificationEventHandler(NotificationRepository repository) {
    this.repository = repository;
  }

  @HandleEvent
  public void onOrderPlaced(OrderPlacedEvent event) {
    String message =
        String.format(
            "Order %s placed: %s x%d for %s",
            event.getOrderId(),
            event.getProductId(),
            event.getQuantity(),
            event.getCustomerEmail());
    log.info("Notification: {}", message);
    repository.save(
        new Notification(UUID.randomUUID().toString(), "ORDER_PLACED", message, Instant.now()));
  }

  @HandleEvent
  public void onInventoryReserved(InventoryReservedEvent event) {
    String message =
        String.format(
            "Inventory reserved for order %s: %d units of %s",
            event.getOrderId(), event.getReservedQuantity(), event.getProductId());
    log.info("Notification: {}", message);
    repository.save(
        new Notification(
            UUID.randomUUID().toString(), "INVENTORY_RESERVED", message, Instant.now()));
  }

  @HandleEvent
  public void onInventoryRejected(InventoryRejectedEvent event) {
    String message =
        String.format(
            "Inventory rejected for order %s (%s): %s",
            event.getOrderId(), event.getProductId(), event.getReason());
    log.warn("Notification: {}", message);
    repository.save(
        new Notification(
            UUID.randomUUID().toString(), "INVENTORY_REJECTED", message, Instant.now()));
  }
}
