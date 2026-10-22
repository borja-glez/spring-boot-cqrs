package com.borjaglez.cqrs.test.architecture.fixtures.bad;

import org.springframework.util.StringUtils;

import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.test.architecture.fixtures.ComposedCommandHandler;

@ComposedCommandHandler
public class ComposedSpringDependentHandler {

  @HandleCommand
  public void handle(Object message) {
    StringUtils.hasText("x");
  }
}
