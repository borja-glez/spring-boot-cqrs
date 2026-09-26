package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.util.List;
import java.util.stream.Stream;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class TestOrderListQueryHandler {

  @HandleQuery
  public List<TestOrder> handle(TestOrderListQuery query) {
    return query.isStreamed()
        ? Stream.of(new TestOrder("o-1"), new TestOrder("o-2")).toList()
        : List.of(new TestOrder("o-1"), new TestOrder("o-2"));
  }
}
