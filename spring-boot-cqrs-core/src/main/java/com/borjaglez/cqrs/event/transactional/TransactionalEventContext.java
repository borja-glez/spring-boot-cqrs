package com.borjaglez.cqrs.event.transactional;

import java.util.ArrayList;
import java.util.List;

import com.borjaglez.cqrs.event.Event;

final class TransactionalEventContext {

  private final List<Event> events = new ArrayList<>();

  void add(Event event) {
    events.add(event);
  }

  List<Event> snapshot() {
    return List.copyOf(events);
  }
}
