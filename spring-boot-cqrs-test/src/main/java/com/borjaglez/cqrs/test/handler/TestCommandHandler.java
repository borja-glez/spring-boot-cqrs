package com.borjaglez.cqrs.test.handler;

import com.borjaglez.cqrs.command.Command;

@FunctionalInterface
public interface TestCommandHandler<C extends Command, R> {

  R handle(C command);
}
