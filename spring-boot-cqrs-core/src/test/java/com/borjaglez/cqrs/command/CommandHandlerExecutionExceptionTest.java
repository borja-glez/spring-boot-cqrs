package com.borjaglez.cqrs.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandHandlerExecutionExceptionTest {

  @Test
  void causeIsPreserved() {
    Exception cause = new Exception("original");
    CommandHandlerExecutionException ex = new CommandHandlerExecutionException(cause);
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
