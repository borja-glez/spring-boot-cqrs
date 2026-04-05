package com.borjaglez.cqrs.example.multiservice.shared.event;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "inventory-service", module = "inventory", name = "inventory-reserved")
public class InventoryReservedEvent extends Event {

  private String orderId;
  private String productId;
  private int reservedQuantity;
}
