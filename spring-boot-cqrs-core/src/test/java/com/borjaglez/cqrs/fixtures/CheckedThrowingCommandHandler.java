package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class CheckedThrowingCommandHandler {

  @HandleCommand
  public void handle(UnannotatedCommand command) throws Exception {
    throw new Exception("checked command error");
  }
}
