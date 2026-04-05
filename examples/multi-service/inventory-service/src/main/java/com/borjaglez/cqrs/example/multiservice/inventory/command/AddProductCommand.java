package com.borjaglez.cqrs.example.multiservice.inventory.command;

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
@CqrsMessage(service = "inventory-service", module = "inventory", name = "add-product")
public class AddProductCommand extends Command {

  @NotBlank private String productId;

  @NotBlank private String name;

  @Min(0)
  private int initialStock;
}
