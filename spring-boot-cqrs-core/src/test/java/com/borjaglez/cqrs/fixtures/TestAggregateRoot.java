package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.event.AggregateRoot;

public class TestAggregateRoot extends AggregateRoot {

  public void doSomething() {
    record(new TestEvent("happened"));
  }
}
