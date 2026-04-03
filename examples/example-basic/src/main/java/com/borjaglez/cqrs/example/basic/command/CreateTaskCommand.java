package com.borjaglez.cqrs.example.basic.command;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.Getter;

@Getter
@CqrsMessage(service = "example", module = "task", name = "create-task")
public class CreateTaskCommand extends Command {

  private final String title;
  private final String description;

  public CreateTaskCommand(String title, String description) {
    super();
    this.title = title;
    this.description = description;
  }
}
