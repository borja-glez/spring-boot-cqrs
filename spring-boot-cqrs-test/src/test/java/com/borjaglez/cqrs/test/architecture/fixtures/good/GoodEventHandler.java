package com.borjaglez.cqrs.test.architecture.fixtures.good;

import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.event.annotation.EventHandler;

@EventHandler
public class GoodEventHandler {

  @HandleEvent
  public void handle(Object message) {

  }
}
