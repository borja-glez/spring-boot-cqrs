package com.borjaglez.cqrs.test.architecture.fixtures.bad;

import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.command.annotation.CommandHandler;
import org.springframework.util.StringUtils;

@CommandHandler
public class SpringDependentCommandHandler {

  @HandleCommand
  public void handle(Object message) {
    StringUtils.hasText("x");
  }
}
