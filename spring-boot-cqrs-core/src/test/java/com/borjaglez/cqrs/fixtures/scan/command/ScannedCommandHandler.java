package com.borjaglez.cqrs.fixtures.scan.command;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

/**
 * Registered only by the handler scan: it carries no stereotype besides {@code @CommandHandler}.
 */
@CommandHandler
public class ScannedCommandHandler {

  @HandleCommand
  public String handle(ScannedCommand command) {
    return "scanned";
  }
}
