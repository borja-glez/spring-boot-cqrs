package com.borjaglez.cqrs.test.architecture.fixtures.bad;

import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

@EventHandler
public class AnnotatedSpringEventHandler {

  private ApplicationContext context;

  @EventListener
  @HandleEvent
  public void handle(Object event) {}
}
