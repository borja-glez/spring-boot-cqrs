package com.borjaglez.cqrs.example.basic.query;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

@CqrsMessage(service = "example", module = "task", name = "get-all-tasks")
public class GetAllTasksQuery extends Query {

  public GetAllTasksQuery() {
    super();
  }
}
