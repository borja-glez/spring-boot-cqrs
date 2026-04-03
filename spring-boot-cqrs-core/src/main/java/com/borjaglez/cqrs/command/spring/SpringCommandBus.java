package com.borjaglez.cqrs.command.spring;

import java.util.List;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.command.CommandHandlerExecutionException;
import com.borjaglez.cqrs.command.registry.CommandHandlerRegistry;
import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;

public class SpringCommandBus implements CommandBus {

  private final CommandHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;

  public SpringCommandBus(CommandHandlerRegistry registry, List<BusMiddleware> middlewares) {
    this.registry = registry;
    this.middlewares = middlewares;
  }

  @Override
  public void dispatch(Command command) {
    try {
      DefaultMiddlewareChain chain =
          new DefaultMiddlewareChain(
              middlewares,
              msg -> {
                registry.handle((Command) msg);
                return null;
              });
      chain.proceed(command);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandHandlerExecutionException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R dispatchAndReceive(Command command) {
    try {
      DefaultMiddlewareChain chain =
          new DefaultMiddlewareChain(middlewares, msg -> registry.handle((Command) msg));
      return (R) chain.proceed(command);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new CommandHandlerExecutionException(e);
    }
  }
}
