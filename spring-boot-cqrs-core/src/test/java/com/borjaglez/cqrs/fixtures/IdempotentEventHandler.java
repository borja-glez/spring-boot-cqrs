package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.idempotency.Idempotent;

@EventHandler
public class IdempotentEventHandler {

  @HandleEvent
  @Idempotent(name = "stock-projector")
  public void on(TestEvent event) {}

  @HandleEvent
  public void notIdempotent(TestEvent event) {}
}
