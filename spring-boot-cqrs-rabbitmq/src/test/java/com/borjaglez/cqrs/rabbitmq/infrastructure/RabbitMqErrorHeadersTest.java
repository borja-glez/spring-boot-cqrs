package com.borjaglez.cqrs.rabbitmq.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

class RabbitMqErrorHeadersTest {

  @Test
  void handlerFailureUnwrapsTheListenerAdapterException() {
    IllegalStateException handlerError = new IllegalStateException("boom");

    assertThat(
            RabbitMqErrorHeaders.handlerFailure(
                new ListenerExecutionFailedException("wrapped", handlerError)))
        .isSameAs(handlerError);
    ListenerExecutionFailedException withoutCause =
        new ListenerExecutionFailedException("no cause", null);
    assertThat(RabbitMqErrorHeaders.handlerFailure(withoutCause)).isSameAs(withoutCause);
    assertThat(RabbitMqErrorHeaders.handlerFailure(handlerError)).isSameAs(handlerError);
  }
}
