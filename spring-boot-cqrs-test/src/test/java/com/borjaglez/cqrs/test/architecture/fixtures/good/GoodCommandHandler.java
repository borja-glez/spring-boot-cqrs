package com.borjaglez.cqrs.test.architecture.fixtures.good;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class GoodCommandHandler {

  @HandleCommand
  public void handle(Object message) {}
}
