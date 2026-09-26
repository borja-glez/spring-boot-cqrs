package com.borjaglez.cqrs.event.transactional;

import java.util.ArrayList;
import java.util.List;

import com.borjaglez.cqrs.event.Event;

final class TransactionalEventContext {

  private final List<Event> events = new ArrayList<>();

  void add(Event event) {
    events.add(event);
  }

  /** The queued events, removed from the context. */
  List<Event> drain() {
    List<Event> drained = List.copyOf(events);
    events.clear();
    return drained;
  }
}
