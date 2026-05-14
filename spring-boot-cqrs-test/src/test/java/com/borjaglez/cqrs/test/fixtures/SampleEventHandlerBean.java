package com.borjaglez.cqrs.test.fixtures;

import java.util.ArrayList;
import java.util.List;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

@EventHandler
public class SampleEventHandlerBean {

  private final List<String> received = new ArrayList<>();

  @HandleEvent
  public void handle(TestEvent event) {
    received.add(event.getPayload());
  }

  public List<String> getReceived() {
    return received;
  }
}
