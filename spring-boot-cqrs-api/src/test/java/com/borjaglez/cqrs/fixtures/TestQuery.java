package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@CqrsMessage(service = "test", module = "order", name = "get_order")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TestQuery extends Query {
  private String data;
}
