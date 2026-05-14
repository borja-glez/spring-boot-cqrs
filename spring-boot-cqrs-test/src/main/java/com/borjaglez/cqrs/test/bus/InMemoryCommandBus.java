package com.borjaglez.cqrs.test.bus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.test.handler.TestCommandHandler;

public final class InMemoryCommandBus implements CommandBus {

  private final Map<Class<? extends Command>, TestCommandHandler<?, ?>> handlers =
      new ConcurrentHashMap<>();

  public <C extends Command, R> InMemoryCommandBus register(
      Class<C> type, TestCommandHandler<C, R> handler) {
    if (handlers.putIfAbsent(type, handler) != null) {
      throw new HandlerAlreadyRegisteredException(type);
    }
    return this;
  }

  @Override
  public void dispatch(Command command) {
    dispatchAndReceive(command);
  }

  @Override
  public void dispatchAndWait(Command command) {
    dispatchAndReceive(command);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R dispatchAndReceive(Command command) {
    TestCommandHandler<Command, R> handler =
        (TestCommandHandler<Command, R>) handlers.get(command.getClass());
    if (handler == null) {
      throw new NoHandlerRegisteredException(command.getClass());
    }
    return handler.handle(command);
  }
}
