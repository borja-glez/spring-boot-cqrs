package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class TestReturningCommandHandler {

  @HandleCommand
  public String handle(TestCommand command) {
    return command.getData();
  }
}
