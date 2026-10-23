package com.borjaglez.cqrs.fixtures.scan.conditional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.fixtures.scan.ScannedCommand;

/** Switched off by a bean named {@value #OVERRIDE}. */
@CommandHandler
@ConditionalOnMissingBean(name = ConditionalCommandHandler.OVERRIDE)
public class ConditionalCommandHandler {

  public static final String OVERRIDE = "conditionalCommandHandlerOverride";

  @HandleCommand
  public String handle(ScannedCommand command) {
    return "conditional";
  }
}
