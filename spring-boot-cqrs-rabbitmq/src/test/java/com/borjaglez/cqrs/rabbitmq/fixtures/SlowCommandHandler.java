package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class SlowCommandHandler {

  /** Answers {@code null} after the given time. */
  @HandleCommand
  public String handle(SlowCommand command) throws InterruptedException {
    Thread.sleep(command.getMillis());
    return null;
  }
}
