package com.borjaglez.cqrs.example.multiservice.shared.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "inventory-service", module = "inventory", name = "reserve-inventory")
public class ReserveInventoryCommand extends Command {

  @NotBlank private String orderId;

  @NotBlank private String productId;

  @Min(1)
  private int quantity;
}
