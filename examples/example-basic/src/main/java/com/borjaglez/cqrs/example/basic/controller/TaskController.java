package com.borjaglez.cqrs.example.basic.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.borjaglez.cqrs.command.CommandBus;
import com.borjaglez.cqrs.example.basic.command.CompleteTaskCommand;
import com.borjaglez.cqrs.example.basic.command.CreateTaskCommand;
import com.borjaglez.cqrs.example.basic.domain.Task;
import com.borjaglez.cqrs.example.basic.query.GetAllTasksQuery;
import com.borjaglez.cqrs.example.basic.query.GetTaskQuery;
import com.borjaglez.cqrs.query.QueryBus;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

  private final CommandBus commandBus;
  private final QueryBus queryBus;

  public TaskController(CommandBus commandBus, QueryBus queryBus) {
    this.commandBus = commandBus;
    this.queryBus = queryBus;
  }

  @PostMapping
  public ResponseEntity<Task> createTask(@RequestBody CreateTaskRequest request) {
    String taskId =
        commandBus.dispatchAndReceive(
            new CreateTaskCommand(request.title(), request.description()));
    Task task = queryBus.ask(new GetTaskQuery(taskId));
    return ResponseEntity.status(HttpStatus.CREATED).body(task);
  }

  @PutMapping("/{id}/complete")
  public ResponseEntity<Map<String, String>> completeTask(@PathVariable String id) {
    commandBus.dispatch(new CompleteTaskCommand(id));
    return ResponseEntity.ok(Map.of("message", "Task completed successfully"));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Task> getTask(@PathVariable String id) {
    Task task = queryBus.ask(new GetTaskQuery(id));
    return ResponseEntity.ok(task);
  }

  @GetMapping
  public ResponseEntity<List<Task>> getAllTasks() {
    List<Task> tasks = queryBus.ask(new GetAllTasksQuery());
    return ResponseEntity.ok(tasks);
  }

  public record CreateTaskRequest(String title, String description) {}
}
