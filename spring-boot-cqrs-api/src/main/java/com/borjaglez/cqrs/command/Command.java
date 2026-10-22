package com.borjaglez.cqrs.command;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

@Getter
public abstract class Command {

  // Not final so deserializers that only populate non-final fields (Jackson 3 by default) keep the
  // identity the message was sent with. There are no setters: the value never changes after
  // construction or deserialization.
  private String commandId;

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
