package com.borjaglez.cqrs.test.fixtures;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class SampleCommandHandlerBean {

  @HandleCommand
  public String handle(TestCommand command) {
    return "handled:" + command.getPayload();
  }
}
