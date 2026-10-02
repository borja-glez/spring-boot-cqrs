package com.borjaglez.cqrs.fixtures;

import java.util.concurrent.atomic.AtomicInteger;

public class CountingCommandHandlers {

  public final AtomicInteger calls = new AtomicInteger();

  public void handle(TestCommand command) {
    calls.incrementAndGet();
  }

  public String handleAndReturn(TestCommand command) {
    return "result-" + calls.incrementAndGet();
  }
}
