package com.borjaglez.cqrs.fixtures.scan.query;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class ScannedQueryHandler {

  @HandleQuery
  public String handle(ScannedQuery query) {
    return "scanned";
  }
}
