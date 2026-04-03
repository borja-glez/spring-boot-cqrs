package com.borjaglez.cqrs.example.middleware.command;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class GreetCommandHandler {

  @HandleCommand
  public String handle(GreetCommand command) {
    return "Hello, " + command.getName() + "!";
  }
}
