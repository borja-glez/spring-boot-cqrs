package com.borjaglez.cqrs.fixtures.scan.application;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.fixtures.scan.ScannedCommand;

@CommandHandler
public class ScannedCommandHandler {

  @HandleCommand
  public String handle(ScannedCommand command) {
    return "application";
  }
}
