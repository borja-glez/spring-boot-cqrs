package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class CheckedThrowingQueryHandler {

  @HandleQuery
  public String handle(TestQuery query) throws Exception {
    throw new Exception("checked query error");
  }
}
