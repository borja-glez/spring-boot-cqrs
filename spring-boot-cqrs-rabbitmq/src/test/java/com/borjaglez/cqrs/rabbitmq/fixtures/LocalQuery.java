package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.query.Query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Not annotated with {@code @CqrsMessage}, so it is not exposed over RabbitMQ by default. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LocalQuery extends Query {

  private String data;
}
