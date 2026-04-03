package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.util.concurrent.atomic.AtomicReference;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

@EventHandler
public class TestEventHandler {

  private final AtomicReference<String> lastHandledData = new AtomicReference<>();

  @HandleEvent
  public void handle(TestEvent event) {
    lastHandledData.set(event.getData());
  }

  public String getLastHandledData() {
    return lastHandledData.get();
  }
}
