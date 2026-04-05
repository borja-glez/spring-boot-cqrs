package com.borjaglez.cqrs.example.multiservice.order.command;

import jakarta.validation.constraints.Email;
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
@CqrsMessage(service = "order-service", module = "order", name = "place-order")
public class PlaceOrderCommand extends Command {

  @NotBlank private String productId;

  @Min(1)
  private int quantity;

  @NotBlank @Email private String customerEmail;
}
