package com.borjaglez.cqrs.test.fixtures;

import com.borjaglez.cqrs.event.Event;

public class TestEvent extends Event {

  private final String payload;

  public TestEvent(String payload) {
    this.payload = payload;
  }

  public String getPayload() {
    return payload;
  }
}
