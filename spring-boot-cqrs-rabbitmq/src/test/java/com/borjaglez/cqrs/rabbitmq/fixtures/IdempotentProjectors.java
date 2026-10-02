package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.util.concurrent.atomic.AtomicInteger;

public class IdempotentProjectors {

  public final AtomicInteger stockCalls = new AtomicInteger();
  public final AtomicInteger emailCalls = new AtomicInteger();
  public final AtomicInteger emailFailuresLeft = new AtomicInteger(1);

  public void stock(TestEvent event) {
    stockCalls.incrementAndGet();
  }

  public void email(TestEvent event) {
    if (emailFailuresLeft.getAndDecrement() > 0) {
      throw new IllegalStateException("mail server down");
    }
    emailCalls.incrementAndGet();
  }
}
