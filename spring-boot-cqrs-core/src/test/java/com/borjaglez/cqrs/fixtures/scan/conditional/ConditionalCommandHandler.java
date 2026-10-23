package com.borjaglez.cqrs.fixtures.scan.conditional;

import org.springframework.context.annotation.Conditional;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.fixtures.scan.command.ScannedCommand;

@CommandHandler
@Conditional(HandlersEnabledCondition.class)
public class ConditionalCommandHandler {

  @HandleCommand
  public String handle(ScannedCommand command) {
    return "conditional";
  }
}
