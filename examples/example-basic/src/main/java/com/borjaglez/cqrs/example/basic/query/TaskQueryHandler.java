package com.borjaglez.cqrs.example.basic.query;

import java.util.List;

import com.borjaglez.cqrs.example.basic.domain.Task;
import com.borjaglez.cqrs.example.basic.domain.TaskRepository;
import com.borjaglez.cqrs.query.annotation.HandleQuery;
import com.borjaglez.cqrs.query.annotation.QueryHandler;

@QueryHandler
public class TaskQueryHandler {

  private final TaskRepository taskRepository;

  public TaskQueryHandler(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  @HandleQuery
  public Task getTask(GetTaskQuery query) {
    return taskRepository
        .findById(query.getTaskId())
        .orElseThrow(() -> new IllegalArgumentException("Task not found: " + query.getTaskId()));
  }

  @HandleQuery
  public List<Task> getAllTasks(GetAllTasksQuery query) {
    return taskRepository.findAll();
  }
}
