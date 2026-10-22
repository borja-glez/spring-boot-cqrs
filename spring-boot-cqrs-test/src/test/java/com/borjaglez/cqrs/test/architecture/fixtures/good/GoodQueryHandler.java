package com.borjaglez.cqrs.test.architecture.fixtures.good;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class GoodQueryHandler {

  @HandleQuery
  public Object handle(Object message) {
    return null;
  }
}
