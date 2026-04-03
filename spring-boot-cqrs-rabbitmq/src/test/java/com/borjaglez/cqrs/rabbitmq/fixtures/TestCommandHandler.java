package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.util.concurrent.atomic.AtomicReference;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class TestCommandHandler {

  private final AtomicReference<String> lastHandledData = new AtomicReference<>();

  @HandleCommand
  public String handle(TestCommand command) {
    lastHandledData.set(command.getData());
    return "handled:" + command.getData();
  }

  public String getLastHandledData() {
    return lastHandledData.get();
  }
}
