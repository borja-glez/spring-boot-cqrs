package com.borjaglez.cqrs.test.architecture.fixtures.bad;

import org.springframework.util.StringUtils;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class SpringDependentCommandHandler {

  @HandleCommand
  public void handle(Object message) {
    StringUtils.hasText("x");
  }
}
