package com.borjaglez.cqrs.event;

public class EventHandlerExecutionException extends RuntimeException {

  public EventHandlerExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
