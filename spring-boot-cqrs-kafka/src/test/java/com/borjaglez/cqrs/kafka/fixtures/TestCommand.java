package com.borjaglez.cqrs.kafka.fixtures;

import com.borjaglez.cqrs.command.Command;

import lombok.Getter;

@Getter
public class TestCommand extends Command {

  private final String value;

  public TestCommand(String value) {
    this.value = value;
  }
}
