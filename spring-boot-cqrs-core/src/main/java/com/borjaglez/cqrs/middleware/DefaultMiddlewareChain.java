package com.borjaglez.cqrs.middleware;

import java.util.List;

/**
 * Immutable middleware chain. The chain at position {@code i} invokes the middleware at {@code i}
 * with a chain positioned at {@code i + 1}, so a middleware may call {@code proceed} any number of
 * times and each call runs every later middleware and the terminal again.
 */
public class DefaultMiddlewareChain implements MiddlewareChain {

  private final List<BusMiddleware> middlewares;
  private final MiddlewareChain terminal;
  private final int position;

  public DefaultMiddlewareChain(List<BusMiddleware> middlewares, MiddlewareChain terminal) {
    this(middlewares, terminal, 0);
  }

  private DefaultMiddlewareChain(
      List<BusMiddleware> middlewares, MiddlewareChain terminal, int position) {
    this.middlewares = middlewares;
    this.terminal = terminal;
    this.position = position;
  }

  @Override
  public Object proceed(Object message) throws Exception {
    if (position < middlewares.size()) {
      return middlewares
          .get(position)
          .process(message, new DefaultMiddlewareChain(middlewares, terminal, position + 1));
    }
    return terminal.proceed(message);
  }
}
