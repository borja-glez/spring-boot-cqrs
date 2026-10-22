package com.borjaglez.cqrs.command;

import com.borjaglez.cqrs.ResultType;

public interface CommandBus {

  void dispatch(Command command);

  void dispatchAndWait(Command command);

  <R> R dispatchAndReceive(Command command);

  /**
   * Dispatches {@code command} and returns its result as {@code resultType}. Remote buses use the
   * type to rebuild a generic result, such as a {@code List<Order>}, with typed elements; local
   * buses return the handler's result as it is, which this default does.
   */
  default <R> R dispatchAndReceive(Command command, ResultType<R> resultType) {
    return dispatchAndReceive(command);
  }
}
