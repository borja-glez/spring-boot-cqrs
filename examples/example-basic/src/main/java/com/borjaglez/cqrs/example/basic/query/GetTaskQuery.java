package com.borjaglez.cqrs.example.basic.query;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

import lombok.Getter;

@Getter
@CqrsMessage(service = "example", module = "task", name = "get-task")
public class GetTaskQuery extends Query {

  private final String taskId;

  public GetTaskQuery(String taskId) {
    super();
    this.taskId = taskId;
  }
}
