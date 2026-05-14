package com.borjaglez.cqrs.test.bus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.assertj.core.api.AssertProvider;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.test.assertion.EventBusAssert;

public final class SpyEventBus implements EventBus, AssertProvider<EventBusAssert> {

  private final EventBus delegate;
  private final List<Event> recorded = new CopyOnWriteArrayList<>();

  public SpyEventBus(EventBus delegate) {
    this.delegate = delegate;
  }

  public List<Event> recorded() {
    return List.copyOf(recorded);
  }

  public void clear() {
    recorded.clear();
  }

  @Override
  public EventBusAssert assertThat() {
    return new EventBusAssert(this);
  }

  @Override
  public void publish(Event event) {
    recorded.add(event);
    delegate.publish(event);
  }

  @Override
  public void publish(List<Event> events) {
    recorded.addAll(events);
    delegate.publish(events);
  }
}
