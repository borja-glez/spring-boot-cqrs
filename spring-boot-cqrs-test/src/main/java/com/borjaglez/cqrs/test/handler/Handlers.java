package com.borjaglez.cqrs.test.handler;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;

public final class Handlers {

  private Handlers() {}

  @SuppressWarnings("unchecked")
  public static <M, R> R invoke(M message, Function<M, R> handler, BusMiddleware... middlewares)
      throws Exception {
    List<BusMiddleware> chainMiddlewares =
        middlewares == null ? List.of() : Arrays.asList(middlewares);
    DefaultMiddlewareChain chain =
        new DefaultMiddlewareChain(chainMiddlewares, msg -> handler.apply((M) msg));
    return (R) chain.proceed(message);
  }
}
