package com.borjaglez.cqrs.test.architecture.fixtures.good;

import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.command.annotation.CommandHandler;

@CommandHandler
public class GoodCommandHandler {

  @HandleCommand
  public void handle(Object message) {

  }
}
