package com.borjaglez.cqrs.test.bus;

public class NoHandlerRegisteredException extends IllegalStateException {

  public NoHandlerRegisteredException(Class<?> messageType) {
    super("No handler registered for " + messageType.getName());
  }
}
