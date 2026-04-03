package com.borjaglez.cqrs.example.basic.event;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;

@Getter
@CqrsMessage(service = "example", module = "task", name = "task-created")
public class TaskCreatedEvent extends Event {

  private final String taskId;
  private final String title;

  public TaskCreatedEvent(String taskId, String title) {
    super();
    this.taskId = taskId;
    this.title = title;
  }
}
