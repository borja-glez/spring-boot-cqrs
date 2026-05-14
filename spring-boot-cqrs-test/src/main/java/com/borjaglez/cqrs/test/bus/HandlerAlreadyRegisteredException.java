package com.borjaglez.cqrs.test.bus;

public class HandlerAlreadyRegisteredException extends IllegalStateException {

  public HandlerAlreadyRegisteredException(Class<?> messageType) {
    super("Handler already registered for " + messageType.getName());
  }
}
