package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class ThrowingCommandHandler {

  @HandleCommand
  public void handle(TestCommand command) {
    throw new IllegalStateException("command handler error");
  }
}
