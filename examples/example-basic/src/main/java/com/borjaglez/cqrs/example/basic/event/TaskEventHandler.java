package com.borjaglez.cqrs.example.basic.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.borjaglez.cqrs.event.annotation.EventHandler;
import com.borjaglez.cqrs.event.annotation.HandleEvent;

@EventHandler
public class TaskEventHandler {

  private static final Logger log = LoggerFactory.getLogger(TaskEventHandler.class);

  @HandleEvent
  public void onTaskCreated(TaskCreatedEvent event) {
    log.info("Task created: {}", event.getTaskId());
  }

  @HandleEvent
  public void onTaskCompleted(TaskCompletedEvent event) {
    log.info("Task completed: {}", event.getTaskId());
  }
}
