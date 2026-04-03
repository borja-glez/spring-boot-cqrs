package com.borjaglez.cqrs.example.basic.command;

import java.util.UUID;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.event.EventBus;
import com.borjaglez.cqrs.example.basic.domain.Task;
import com.borjaglez.cqrs.example.basic.domain.TaskRepository;
import com.borjaglez.cqrs.example.basic.event.TaskCreatedEvent;

@CommandHandler
public class CreateTaskCommandHandler {

  private final TaskRepository taskRepository;
  private final EventBus eventBus;

  public CreateTaskCommandHandler(TaskRepository taskRepository, EventBus eventBus) {
    this.taskRepository = taskRepository;
    this.eventBus = eventBus;
  }

  @HandleCommand
  public String handle(CreateTaskCommand command) {
    String taskId = UUID.randomUUID().toString();
    Task task = new Task(taskId, command.getTitle(), command.getDescription());
    taskRepository.save(task);
    eventBus.publish(new TaskCreatedEvent(taskId, command.getTitle()));
    return taskId;
  }
}
