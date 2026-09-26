package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class LocalCommandHandler {

  private final List<String> handled = new CopyOnWriteArrayList<>();

  @HandleCommand
  public String handle(LocalCommand command) {
    handled.add(command.getData());
    return "local:" + command.getData();
  }

  @HandleCommand(remote = false)
  public String handle(InternalCommand command) {
    handled.add(command.getData());
    return "internal:" + command.getData();
  }

  public List<String> getHandled() {
    return handled;
  }
}
