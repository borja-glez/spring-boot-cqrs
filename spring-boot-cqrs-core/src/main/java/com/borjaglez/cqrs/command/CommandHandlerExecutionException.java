package com.borjaglez.cqrs.command;

public class CommandHandlerExecutionException extends RuntimeException {

  public CommandHandlerExecutionException(Throwable cause) {
    super(cause);
  }
}
