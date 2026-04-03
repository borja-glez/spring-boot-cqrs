package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

@EventHandler
public class CheckedThrowingEventHandler {

  @HandleEvent
  public void handle(TestEvent event) throws Exception {
    throw new Exception("checked event error");
  }
}
