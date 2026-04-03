package com.borjaglez.cqrs.example.basic.command;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.example.basic.domain.Task;
import com.borjaglez.cqrs.example.basic.domain.TaskRepository;
import com.borjaglez.cqrs.example.basic.event.TaskCompletedEvent;

@CommandHandler
public class CompleteTaskCommandHandler {

  private final TaskRepository taskRepository;
  private final EventBus eventBus;

  public CompleteTaskCommandHandler(TaskRepository taskRepository, EventBus eventBus) {
    this.taskRepository = taskRepository;
    this.eventBus = eventBus;
  }

  @HandleCommand
  public void handle(CompleteTaskCommand command) {
    Task task =
        taskRepository
            .findById(command.getTaskId())
            .orElseThrow(
                () -> new IllegalArgumentException("Task not found: " + command.getTaskId()));
    task.setCompleted(true);
    taskRepository.save(task);
    eventBus.publish(new TaskCompletedEvent(task.getId()));
  }
}
