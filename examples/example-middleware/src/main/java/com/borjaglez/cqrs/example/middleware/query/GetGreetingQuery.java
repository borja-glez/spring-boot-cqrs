package com.borjaglez.cqrs.example.middleware.query;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

import lombok.Getter;

@Getter
@CqrsMessage(service = "middleware-demo", module = "greeting", name = "get-greeting")
public class GetGreetingQuery extends Query {

  private final String name;

  public GetGreetingQuery(String name) {
    this.name = name;
  }
}
