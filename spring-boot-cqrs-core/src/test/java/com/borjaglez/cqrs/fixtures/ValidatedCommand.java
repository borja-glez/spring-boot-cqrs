package com.borjaglez.cqrs.fixtures;

import jakarta.validation.constraints.NotBlank;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@CqrsMessage(service = "test", module = "order", name = "validated_order")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ValidatedCommand extends Command {
  @NotBlank private String name;
}
