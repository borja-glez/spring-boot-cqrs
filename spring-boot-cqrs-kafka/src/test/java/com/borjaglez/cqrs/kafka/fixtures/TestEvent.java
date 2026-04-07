package com.borjaglez.cqrs.kafka.fixtures;

import com.borjaglez.cqrs.event.Event;

import lombok.Getter;

@Getter
public class TestEvent extends Event {

  private final String value;

  public TestEvent(String value) {
    this.value = value;
  }
}
