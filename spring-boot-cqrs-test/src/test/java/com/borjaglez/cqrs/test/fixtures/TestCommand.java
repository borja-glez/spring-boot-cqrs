package com.borjaglez.cqrs.test.fixtures;

import com.borjaglez.cqrs.command.Command;

public class TestCommand extends Command {

  private final String payload;

  public TestCommand(String payload) {
    this.payload = payload;
  }

  public String getPayload() {
    return payload;
  }
}
