package com.borjaglez.cqrs.kafka.fixtures;

import jakarta.validation.constraints.NotBlank;

import com.borjaglez.cqrs.command.Command;

import lombok.Getter;

@Getter
public class ValidatedCommand extends Command {

  @NotBlank private final String customerId;

  public ValidatedCommand(String customerId) {
    this.customerId = customerId;
  }
}
