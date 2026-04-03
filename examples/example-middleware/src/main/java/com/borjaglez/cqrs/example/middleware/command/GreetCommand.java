package com.borjaglez.cqrs.example.middleware.command;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;

@Getter
@CqrsMessage(service = "middleware-demo", module = "greeting", name = "greet")
public class GreetCommand extends Command {

  private final String name;

  public GreetCommand(String name) {
    this.name = name;
  }
}
