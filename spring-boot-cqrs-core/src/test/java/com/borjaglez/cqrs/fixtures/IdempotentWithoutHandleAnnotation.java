package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.idempotency.Idempotent;

@EventHandler
public class IdempotentWithoutHandleAnnotation {

  @Idempotent
  public void on(TestEvent event) {}
}
