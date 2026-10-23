package com.borjaglez.cqrs.test.architecture.fixtures.bad;

import com.borjaglez.cqrs.query.annotation.HandleQuery;

public class MisplacedQueryHandlerMethod {

  @HandleQuery
  public Object handle(Object message) {
    return null;
  }
}
