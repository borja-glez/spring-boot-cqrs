package com.borjaglez.cqrs.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestQuery;

class QueryAlreadyRegisteredExceptionTest {

  @Test
  void messageContainsClassName() {
    QueryAlreadyRegisteredException ex = new QueryAlreadyRegisteredException(TestQuery.class);
    assertThat(ex.getMessage()).contains(TestQuery.class.getName());
  }
}
