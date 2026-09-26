package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class FailingCommandHandler {

  @HandleCommand
  public String handle(FailingCommand command) {
    throw new IllegalStateException(command.getReason());
  }
}
