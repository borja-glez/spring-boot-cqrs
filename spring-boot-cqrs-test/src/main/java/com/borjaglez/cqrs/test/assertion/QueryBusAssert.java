package com.borjaglez.cqrs.test.assertion;

import java.util.List;
import java.util.function.Predicate;

import org.assertj.core.api.AbstractAssert;

import com.borjaglez.cqrs.query.Query;
import com.borjaglez.cqrs.test.bus.SpyQueryBus;

public class QueryBusAssert extends AbstractAssert<QueryBusAssert, SpyQueryBus> {

  private List<Query> filtered;
  private String filterDescription = "any query";

  public QueryBusAssert(SpyQueryBus actual) {
    super(actual, QueryBusAssert.class);
    isNotNull();
    this.filtered = actual.recorded();
  }

  public QueryBusAssert asked(Class<? extends Query> type) {
    this.filtered = filtered.stream().filter(type::isInstance).toList();
    this.filterDescription = type.getName();
    return this;
  }

  public QueryBusAssert once() {
    return times(1);
  }

  public QueryBusAssert never() {
    return times(0);
  }

  public QueryBusAssert times(int expected) {
    int actualCount = filtered.size();
    if (actualCount != expected) {
      throw failure(
          "Expected %d ask(s) of %s but got %d", expected, filterDescription, actualCount);
    }
    return this;
  }

  public QueryBusAssert matching(Predicate<? super Query> predicate) {
    if (filtered.stream().noneMatch(predicate)) {
      throw failure("No asked query of %s matched the given predicate", filterDescription);
    }
    return this;
  }
}
