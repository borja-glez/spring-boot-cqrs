package com.borjaglez.cqrs.command;

public class CommandNotRegisteredException extends RuntimeException {

  public CommandNotRegisteredException(Class<?> commandClass) {
    super("No handler registered for command: " + commandClass.getName());
  }
}
