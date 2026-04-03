package com.borjaglez.cqrs.example.middleware.query;

import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class GetGreetingQueryHandler {

  @HandleQuery
  public String handle(GetGreetingQuery query) {
    return "Hello, " + query.getName() + "! Welcome!";
  }
}
