package com.borjaglez.cqrs.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestCommand;

class CommandNotRegisteredExceptionTest {

  @Test
  void messageContainsClassName() {
    CommandNotRegisteredException ex = new CommandNotRegisteredException(TestCommand.class);
    assertThat(ex.getMessage()).contains(TestCommand.class.getName());
  }
}
