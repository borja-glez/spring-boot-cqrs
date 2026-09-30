package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class SlowQueryHandler {

  /** Answers after the given time. */
  @HandleQuery
  public String handle(SlowQuery query) throws InterruptedException {
    Thread.sleep(query.getMillis());
    return "slow:" + query.getMillis();
  }
}
