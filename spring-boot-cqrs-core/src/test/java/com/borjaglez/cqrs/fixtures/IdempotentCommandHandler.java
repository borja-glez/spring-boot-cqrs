package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.idempotency.Idempotent;

@CommandHandler
public class IdempotentCommandHandler {

  @HandleCommand
  @Idempotent
  public void handle(TestCommand command) {}
}
