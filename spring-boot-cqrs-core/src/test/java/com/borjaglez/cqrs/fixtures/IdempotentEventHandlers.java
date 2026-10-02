package com.borjaglez.cqrs.fixtures;

import java.util.concurrent.atomic.AtomicInteger;

/** Two handlers of the same event; {@code second} fails the first {@code failuresLeft} times. */
public class IdempotentEventHandlers {

  public final AtomicInteger firstCalls = new AtomicInteger();
  public final AtomicInteger secondCalls = new AtomicInteger();
  public final AtomicInteger failuresLeft = new AtomicInteger();

  public void first(TestEvent event) {
    firstCalls.incrementAndGet();
  }

  public void second(TestEvent event) {
    if (failuresLeft.getAndDecrement() > 0) {
      throw new IllegalStateException("second failed");
    }
    secondCalls.incrementAndGet();
  }
}
