package com.borjaglez.cqrs.test.fixtures;

import com.borjaglez.cqrs.query.Query;

public class TestQuery extends Query {

  private final String payload;

  public TestQuery(String payload) {
    this.payload = payload;
  }

  public String getPayload() {
    return payload;
  }
}
