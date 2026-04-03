package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class TestQueryHandler {

  @HandleQuery
  public String handle(TestQuery query) {
    return "result:" + query.getData();
  }
}
