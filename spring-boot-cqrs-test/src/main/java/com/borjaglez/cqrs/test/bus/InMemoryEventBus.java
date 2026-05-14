package com.borjaglez.cqrs.test.bus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.test.handler.TestEventHandler;

public final class InMemoryEventBus implements EventBus {

  private final Map<Class<? extends Event>, List<TestEventHandler<?>>> handlers =
      new ConcurrentHashMap<>();

  public <E extends Event> InMemoryEventBus subscribe(Class<E> type, TestEventHandler<E> handler) {
    handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void publish(Event event) {
    List<TestEventHandler<?>> matched = handlers.get(event.getClass());
    if (matched == null) {
      return;
    }
    for (TestEventHandler<?> raw : matched) {
      ((TestEventHandler<Event>) raw).handle(event);
    }
  }

  @Override
  public void publish(List<Event> events) {
    for (Event event : events) {
      publish(event);
    }
  }
}
