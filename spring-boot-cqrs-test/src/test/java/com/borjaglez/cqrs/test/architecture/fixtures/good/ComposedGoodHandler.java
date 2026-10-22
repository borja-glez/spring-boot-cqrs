package com.borjaglez.cqrs.test.architecture.fixtures.good;

import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.test.architecture.fixtures.ComposedCommandHandler;

@ComposedCommandHandler
public class ComposedGoodHandler {

  @HandleCommand
  public void handle(Object message) {}
}
