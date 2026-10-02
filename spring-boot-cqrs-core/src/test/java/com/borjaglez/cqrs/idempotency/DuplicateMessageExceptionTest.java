package com.borjaglez.cqrs.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DuplicateMessageExceptionTest {

  @Test
  void namesTheHandlerAndTheMessage() {
    DuplicateMessageException exception = new DuplicateMessageException("orders#handle", "id-1");

    assertThat(exception.getHandlerId()).isEqualTo("orders#handle");
    assertThat(exception.getMessageId()).isEqualTo("id-1");
    assertThat(exception)
        .hasMessage("Message id-1 was already processed by idempotent handler orders#handle");
  }
}
