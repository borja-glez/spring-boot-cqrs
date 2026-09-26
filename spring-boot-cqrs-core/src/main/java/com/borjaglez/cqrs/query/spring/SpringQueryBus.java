package com.borjaglez.cqrs.query.spring;

import java.util.List;

import com.borjaglez.cqrs.middleware.BusMiddleware;
import com.borjaglez.cqrs.middleware.DefaultMiddlewareChain;
import com.borjaglez.cqrs.middleware.DispatchPhase;
import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.query.QueryHandlerExecutionException;
import com.borjaglez.cqrs.query.registry.QueryHandlerRegistry;

public class SpringQueryBus implements QueryBus {

  private final QueryHandlerRegistry registry;
  private final List<BusMiddleware> middlewares;

  /**
   * Creates the bus. Only the middlewares of {@code middlewares} that declare {@link
   * DispatchPhase#LOCAL} run, in the order of the list.
   */
  public SpringQueryBus(QueryHandlerRegistry registry, List<BusMiddleware> middlewares) {
    this.registry = registry;
    this.middlewares = DispatchPhase.LOCAL.select(middlewares);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R ask(Query query) {
    try {
      DefaultMiddlewareChain chain =
          new DefaultMiddlewareChain(middlewares, msg -> registry.handle((Query) msg));
      return (R) chain.proceed(query);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new QueryHandlerExecutionException(e);
    }
  }
}
