package com.borjaglez.cqrs.fixtures.scan.componentscan;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.fixtures.scan.ScannedCommand;

@CommandHandler
public class ComponentScannedCommandHandler {

  @HandleCommand
  public String handle(ScannedCommand command) {
    return "component-scan";
  }
}
