package com.borjaglez.cqrs.command;

public class CommandNotRegisteredException extends RuntimeException {

  public CommandNotRegisteredException(Class<?> commandClass) {
    super("No handler registered for command: " + commandClass.getName());
  }

  /**
   * Creates the exception for a command that has no handler for its exact class while {@code
   * handledSuperclass} has one. Handlers match exact message classes, so that handler does not
   * receive subclasses.
   */
  public CommandNotRegisteredException(Class<?> commandClass, Class<?> handledSuperclass) {
    super(
        "No handler registered for command: "
            + commandClass.getName()
            + "; a handler is registered for its superclass "
            + handledSuperclass.getName()
            + ", but handlers match the exact message class, so it does not receive subclasses."
            + " Register a handler for "
            + commandClass.getName());
  }
}
