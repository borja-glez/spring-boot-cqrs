package com.borjaglez.cqrs.middleware;

import java.util.List;

public class DefaultMiddlewareChain implements MiddlewareChain {

  private final List<BusMiddleware> middlewares;
  private final MiddlewareChain terminal;
  private int index;

  public DefaultMiddlewareChain(List<BusMiddleware> middlewares, MiddlewareChain terminal) {
    this.middlewares = middlewares;
    this.terminal = terminal;
    this.index = 0;
  }

  @Override
  public Object proceed(Object message) throws Exception {
    if (index < middlewares.size()) {
      return middlewares.get(index++).process(message, this);
    }
    return terminal.proceed(message);
  }
}
