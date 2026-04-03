package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class ThrowingQueryHandler {

  @HandleQuery
  public String handle(TestQuery query) {
    throw new IllegalStateException("query handler error");
  }
}
