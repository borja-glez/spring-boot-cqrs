package com.borjaglez.cqrs.kafka.fixtures;

import com.borjaglez.cqrs.query.Query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Asks for a {@code List<TestOrder>}, built with {@code List.of} or {@code Stream.toList}. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TestOrderListQuery extends Query {

  private boolean streamed;
}
