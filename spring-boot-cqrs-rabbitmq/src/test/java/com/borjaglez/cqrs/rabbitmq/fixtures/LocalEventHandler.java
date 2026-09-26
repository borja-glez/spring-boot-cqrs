package com.borjaglez.cqrs.rabbitmq.fixtures;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

@EventHandler
public class LocalEventHandler {

  private final List<String> handled = new CopyOnWriteArrayList<>();

  @HandleEvent
  public void on(LocalEvent event) {
    handled.add(event.getData());
  }

  /** A local-only handler of an exposed event: remote deliveries of the event skip it. */
  @HandleEvent(remote = false)
  public void on(TestEvent event) {
    handled.add(event.getData());
  }

  public List<String> getHandled() {
    return handled;
  }
}
