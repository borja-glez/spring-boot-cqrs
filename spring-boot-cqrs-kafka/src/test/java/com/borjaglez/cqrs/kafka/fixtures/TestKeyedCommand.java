package com.borjaglez.cqrs.kafka.fixtures;

import com.borjaglez.cqrs.KeyedMessage;
import com.borjaglez.cqrs.command.Command;

public class TestKeyedCommand extends Command implements KeyedMessage {

  private final String key;

  public TestKeyedCommand(String key) {
    this.key = key;
  }

  @Override
  public String messageKey() {
    return key;
  }
}
