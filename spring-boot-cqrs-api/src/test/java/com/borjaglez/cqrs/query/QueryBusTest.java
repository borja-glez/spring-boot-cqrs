package com.borjaglez.cqrs.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.ResultType;
import com.borjaglez.cqrs.fixtures.TestQuery;

class QueryBusTest {

  @Test
  void askWithResultTypeDelegatesToAsk() {
    QueryBus bus =
        new QueryBus() {
          @Override
          @SuppressWarnings("unchecked")
          public <R> R ask(Query query) {
            return (R) ("answer:" + ((TestQuery) query).getData());
          }
        };

    String result = bus.ask(new TestQuery("data"), new ResultType<String>() {});

    assertThat(result).isEqualTo("answer:data");
  }
}
