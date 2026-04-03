package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class TestCommandHandler {

  private String lastHandledData;

  @HandleCommand
  public void handle(TestCommand command) {
    this.lastHandledData = command.getData();
  }

  public String getLastHandledData() {
    return lastHandledData;
  }
}
