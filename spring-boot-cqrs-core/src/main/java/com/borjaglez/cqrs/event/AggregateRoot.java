package com.borjaglez.cqrs.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AggregateRoot {

  private final transient List<Event> events = new ArrayList<>();

  public List<Event> pullEvents() {
    List<Event> snapshot = Collections.unmodifiableList(new ArrayList<>(events));
    events.clear();
    return snapshot;
  }

  protected void record(Event event) {
    events.add(event);
  }
}
