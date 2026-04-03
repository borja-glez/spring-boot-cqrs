package com.borjaglez.cqrs.example.basic.event;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;

@Getter
@CqrsMessage(service = "example", module = "task", name = "task-completed")
public class TaskCompletedEvent extends Event {

  private final String taskId;

  public TaskCompletedEvent(String taskId) {
    super();
    this.taskId = taskId;
  }
}
