package com.borjaglez.cqrs.command;

public class CommandAlreadyRegisteredException extends RuntimeException {

  public CommandAlreadyRegisteredException(Class<?> commandClass) {
    super("A handler is already registered for command: " + commandClass.getName());
  }
}
