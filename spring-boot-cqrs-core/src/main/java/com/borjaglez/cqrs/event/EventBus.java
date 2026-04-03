package com.borjaglez.cqrs.event;

import java.util.List;

public interface EventBus {

  void publish(Event event);

  void publish(List<Event> events);
}
