package com.borjaglez.cqrs.test.handler;

import com.borjaglez.cqrs.event.Event;

@FunctionalInterface
public interface TestEventHandler<E extends Event> {

  void handle(E event);
}
