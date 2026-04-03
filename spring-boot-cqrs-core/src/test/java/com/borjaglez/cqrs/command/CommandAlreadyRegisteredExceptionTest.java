package com.borjaglez.cqrs.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestCommand;

class CommandAlreadyRegisteredExceptionTest {

  @Test
  void messageContainsClassName() {
    CommandAlreadyRegisteredException ex = new CommandAlreadyRegisteredException(TestCommand.class);
    assertThat(ex.getMessage()).contains(TestCommand.class.getName());
  }
}
