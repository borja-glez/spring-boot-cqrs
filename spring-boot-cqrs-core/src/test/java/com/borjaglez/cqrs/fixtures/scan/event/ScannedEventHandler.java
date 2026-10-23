package com.borjaglez.cqrs.fixtures.scan.event;

import org.springframework.context.annotation.Lazy;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

/** Lazy, to check that the scan applies the bean definition annotations a component scan would. */
@EventHandler
@Lazy
public class ScannedEventHandler {

  @HandleEvent
  public void on(ScannedEvent event) {
    // Nothing to do: the tests only check that the handler is registered.
  }
}
