package com.borjaglez.cqrs.query;

import java.util.Objects;

import com.borjaglez.cqrs.ResultType;

public interface QueryBus {

  <R> R ask(Query query);

  /**
   * Asks {@code query} and returns its result as {@code resultType}. Remote buses use the type to
   * rebuild a generic result, such as a {@code List<Order>}, with typed elements; local buses
   * return the handler's result as it is, which this default does.
   */
  default <R> R ask(Query query, ResultType<R> resultType) {
    Objects.requireNonNull(resultType, "resultType must not be null");
    return ask(query);
  }
}
