package com.borjaglez.cqrs.test.fixtures;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class SampleQueryHandlerBean {

  @HandleQuery
  public String handle(TestQuery query) {
    return "answer:" + query.getPayload();
  }
}
