package com.borjaglez.cqrs.example.multiservice.shared.event;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "inventory-service", module = "inventory", name = "inventory-rejected")
public class InventoryRejectedEvent extends Event {

  private String orderId;
  private String productId;
  private String reason;
}
