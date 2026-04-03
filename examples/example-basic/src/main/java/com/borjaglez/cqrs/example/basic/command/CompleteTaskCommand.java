package com.borjaglez.cqrs.example.basic.command;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;

@Getter
@CqrsMessage(service = "example", module = "task", name = "complete-task")
public class CompleteTaskCommand extends Command {

  private final String taskId;

  public CompleteTaskCommand(String taskId) {
    super();
    this.taskId = taskId;
  }
}
