package com.borjaglez.cqrs.test.assertion;

import java.util.List;
import java.util.function.Predicate;

import org.assertj.core.api.AbstractAssert;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.test.bus.SpyEventBus;

public class EventBusAssert extends AbstractAssert<EventBusAssert, SpyEventBus> {

  private List<Event> filtered;
  private String filterDescription = "any event";

  public EventBusAssert(SpyEventBus actual) {
    super(actual, EventBusAssert.class);
    this.filtered = actual.recorded();
  }

  public EventBusAssert published(Class<? extends Event> type) {
    this.filtered = filtered.stream().filter(type::isInstance).toList();
    this.filterDescription = type.getName();
    return this;
  }

  public EventBusAssert once() {
    return times(1);
  }

  public EventBusAssert never() {
    return times(0);
  }

  public EventBusAssert times(int expected) {
    int actualCount = filtered.size();
    if (actualCount != expected) {
      throw failure(
          "Expected %d publication(s) of %s but got %d", expected, filterDescription, actualCount);
    }
    return this;
  }

  public EventBusAssert matching(Predicate<? super Event> predicate) {
    if (filtered.stream().noneMatch(predicate)) {
      throw failure("No published event of %s matched the given predicate", filterDescription);
    }
    return this;
  }
}
