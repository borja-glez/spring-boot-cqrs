package com.borjaglez.cqrs.fixtures;

import jakarta.validation.Valid;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class ValidatedCommandHandler {

  @HandleCommand
  public void handle(@Valid ValidatedCommand command) {
    // no-op
  }
}
