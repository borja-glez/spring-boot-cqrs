package com.borjaglez.cqrs.kafka.fixtures;

import com.borjaglez.cqrs.query.Query;

import lombok.Getter;

@Getter
public class TestQuery extends Query {

  private final String value;

  public TestQuery(String value) {
    this.value = value;
  }
}
