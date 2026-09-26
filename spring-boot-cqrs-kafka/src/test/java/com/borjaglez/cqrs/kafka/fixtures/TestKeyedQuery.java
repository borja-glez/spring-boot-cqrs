package com.borjaglez.cqrs.kafka.fixtures;

import com.borjaglez.cqrs.KeyedMessage;
import com.borjaglez.cqrs.query.Query;

public class TestKeyedQuery extends Query implements KeyedMessage {

  private final String key;

  public TestKeyedQuery(String key) {
    this.key = key;
  }

  @Override
  public String messageKey() {
    return key;
  }
}
