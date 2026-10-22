package com.borjaglez.cqrs.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.borjaglez.cqrs.fixtures.TestCommand;

class CommandTest {

  @Test
  void commandIdIsGenerated() {
    TestCommand command = new TestCommand("data");
    assertThat(command.getCommandId()).isNotNull().isNotEmpty();
  }

  @Test
  void twoCommandsHaveDifferentIds() {
    TestCommand command1 = new TestCommand("data");
    TestCommand command2 = new TestCommand("data");
    assertThat(command1.getCommandId()).isNotEqualTo(command2.getCommandId());
  }

  @Test
  void equalsBasedOnCommandId() {
    TestCommand command = new TestCommand("data");
    assertThat(command).isEqualTo(command);
  }

  @Test
  void notEqualToDifferentCommand() {
    TestCommand command1 = new TestCommand("data");
    TestCommand command2 = new TestCommand("data");
    assertThat(command1).isNotEqualTo(command2);
  }

  @Test
  void notEqualToNull() {
    TestCommand command = new TestCommand("data");
    assertThat(command).isNotEqualTo(null);
  }

  @Test
  void notEqualToDifferentType() {
    TestCommand command = new TestCommand("data");
    assertThat(command).isNotEqualTo("not a command");
  }

  @Test
  void hashCodeBasedOnCommandId() {
    TestCommand command = new TestCommand("data");
    assertThat(command.hashCode()).isEqualTo(command.hashCode());
  }

  @Test
  void differentCommandsHaveDifferentHashCodes() {
    TestCommand command1 = new TestCommand("data");
    TestCommand command2 = new TestCommand("data");
    assertThat(command1.hashCode()).isNotEqualTo(command2.hashCode());
  }

  @Test
  void dataIsStored() {
    TestCommand command = new TestCommand("hello");
    assertThat(command.getData()).isEqualTo("hello");
  }

  @Test
  void toStringContainsClassName() {
    TestCommand command = new TestCommand("data");
    assertThat(command.toString()).contains("TestCommand").contains(command.getCommandId());
  }
}
