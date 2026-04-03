package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

@EventHandler
public class ThrowingEventHandler {

  @HandleEvent
  public void handle(TestEvent event) {
    throw new IllegalStateException("event handler error");
  }
}
