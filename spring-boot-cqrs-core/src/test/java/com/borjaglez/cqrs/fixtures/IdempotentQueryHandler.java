package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.idempotency.Idempotent;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class IdempotentQueryHandler {

  @HandleQuery
  @Idempotent
  public String handle(TestQuery query) {
    return "x";
  }
}
