package com.borjaglez.cqrs.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventHandlerExecutionExceptionTest {

  @Test
  void messageAndCausePreserved() {
    Exception cause = new Exception("original");
    EventHandlerExecutionException ex =
        new EventHandlerExecutionException("failed to handle", cause);
    assertThat(ex.getMessage()).isEqualTo("failed to handle");
    assertThat(ex.getCause()).isSameAs(cause);
  }
}
