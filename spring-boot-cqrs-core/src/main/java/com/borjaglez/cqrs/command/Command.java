package com.borjaglez.cqrs.command;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

@Getter
public abstract class Command {

  private final String commandId;

  protected Command() {
    this.commandId = UUID.randomUUID().toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Command command)) return false;
    return Objects.equals(commandId, command.commandId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(commandId);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{commandId='" + commandId + "'}";
  }
}
