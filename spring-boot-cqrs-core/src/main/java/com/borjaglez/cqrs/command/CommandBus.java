package com.borjaglez.cqrs.command;

import org.springframework.core.ParameterizedTypeReference;

public interface CommandBus {

  void dispatch(Command command);

  void dispatchAndWait(Command command);

  <R> R dispatchAndReceive(Command command);

  default <R> R dispatchAndReceive(Command command, ParameterizedTypeReference<R> responseType) {
    return dispatchAndReceive(command);
  }
}
