package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.util.concurrent.atomic.AtomicReference;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class TestQueryHandler {

  private final AtomicReference<String> lastHandledData = new AtomicReference<>();

  @HandleQuery
  public String handle(TestQuery query) {
    lastHandledData.set(query.getData());
    return "result:" + query.getData();
  }

  public String getLastHandledData() {
    return lastHandledData.get();
  }
}
