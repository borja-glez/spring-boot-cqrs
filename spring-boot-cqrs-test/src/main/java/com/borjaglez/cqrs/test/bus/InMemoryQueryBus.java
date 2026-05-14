package com.borjaglez.cqrs.test.bus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.test.handler.TestQueryHandler;

public final class InMemoryQueryBus implements QueryBus {

  private final Map<Class<? extends Query>, TestQueryHandler<?, ?>> handlers =
      new ConcurrentHashMap<>();

  public <Q extends Query, R> InMemoryQueryBus register(
      Class<Q> type, TestQueryHandler<Q, R> handler) {
    handlers.put(type, handler);
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <R> R ask(Query query) {
    TestQueryHandler<Query, R> handler =
        (TestQueryHandler<Query, R>) handlers.get(query.getClass());
    if (handler == null) {
      throw new NoHandlerRegisteredException(query.getClass());
    }
    return handler.handle(query);
  }
}
