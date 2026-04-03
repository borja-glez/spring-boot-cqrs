package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

@EventHandler
public class TestEventHandler {

  private String lastHandledData;

  @HandleEvent
  public void handle(TestEvent event) {
    this.lastHandledData = event.getData();
  }

  public String getLastHandledData() {
    return lastHandledData;
  }
}
