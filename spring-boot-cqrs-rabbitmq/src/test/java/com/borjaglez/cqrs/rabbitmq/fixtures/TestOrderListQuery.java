package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.naming.CqrsMessage;
import com.borjaglez.cqrs.query.Query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Asks for a {@code List<TestOrder>}, built with {@code List.of} or {@code Stream.toList}. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "test", module = "order", name = "list")
public class TestOrderListQuery extends Query {

  private boolean streamed;
}
