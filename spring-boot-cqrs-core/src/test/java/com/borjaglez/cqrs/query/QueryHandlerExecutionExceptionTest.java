package com.borjaglez.cqrs.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QueryHandlerExecutionExceptionTest {

  @Test
  void causeIsPreserved() {
    Exception cause = new Exception("original");
    QueryHandlerExecutionException ex = new QueryHandlerExecutionException(cause);
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
