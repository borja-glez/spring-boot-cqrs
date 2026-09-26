package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class LocalQueryHandler {

  private final List<String> handled = new CopyOnWriteArrayList<>();

  @HandleQuery
  public String handle(LocalQuery query) {
    handled.add(query.getData());
    return "local:" + query.getData();
  }

  public List<String> getHandled() {
    return handled;
  }
}
