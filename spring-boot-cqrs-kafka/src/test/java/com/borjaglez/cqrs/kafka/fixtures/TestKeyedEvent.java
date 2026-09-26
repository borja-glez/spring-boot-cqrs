package com.borjaglez.cqrs.kafka.fixtures;

import com.borjaglez.cqrs.KeyedMessage;
import com.borjaglez.cqrs.event.Event;

public class TestKeyedEvent extends Event implements KeyedMessage {

  private final String key;

  public TestKeyedEvent(String key) {
    this.key = key;
  }

  @Override
  public String messageKey() {
    return key;
  }
}
