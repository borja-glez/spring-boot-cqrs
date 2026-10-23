package com.borjaglez.cqrs.fixtures.scan.inheritance.base;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;
import com.borjaglez.cqrs.fixtures.scan.event.ScannedEvent;

/** A concrete handler that another handler extends. */
@EventHandler
public class AuditEventHandler {

  @HandleEvent
  public void on(ScannedEvent event) {
    // Nothing to do: the tests only check that the handler is registered.
  }
}
