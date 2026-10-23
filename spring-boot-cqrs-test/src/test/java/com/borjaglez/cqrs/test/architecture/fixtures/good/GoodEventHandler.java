package com.borjaglez.cqrs.test.architecture.fixtures.good;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

@EventHandler
public class GoodEventHandler {

  @HandleEvent
  public void handle(Object message) {}
}
