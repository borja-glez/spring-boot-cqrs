package com.borjaglez.cqrs.test.bus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.assertj.core.api.AssertProvider;
import org.springframework.core.ParameterizedTypeReference;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.query.QueryBus;
import com.borjaglez.cqrs.test.assertion.QueryBusAssert;

public final class SpyQueryBus implements QueryBus, AssertProvider<QueryBusAssert> {

  private final QueryBus delegate;
  private final List<Query> recorded = new CopyOnWriteArrayList<>();

  public SpyQueryBus(QueryBus delegate) {
    this.delegate = delegate;
  }

  public List<Query> recorded() {
    return List.copyOf(recorded);
  }

  public void clear() {
    recorded.clear();
  }

  @Override
  public QueryBusAssert assertThat() {
    return new QueryBusAssert(this);
  }

  @Override
  public <R> R ask(Query query) {
    recorded.add(query);
    return delegate.ask(query);
  }

  @Override
  public <R> R ask(Query query, ParameterizedTypeReference<R> responseType) {
    recorded.add(query);
    return delegate.ask(query, responseType);
  }
}
