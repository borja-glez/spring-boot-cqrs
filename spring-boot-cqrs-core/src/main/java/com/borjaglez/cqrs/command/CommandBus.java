package com.borjaglez.cqrs.command;

public interface CommandBus {

  void dispatch(Command command);

  <R> R dispatchAndReceive(Command command);
}
