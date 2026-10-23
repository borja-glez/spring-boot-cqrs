package com.borjaglez.cqrs.fixtures.scan.later;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

/** In a package that only {@link LaterPackageAutoConfiguration} makes an application package. */
@CommandHandler
public class LaterCommandHandler {

  @HandleCommand
  public String handle(LaterCommand command) {
    return "later";
  }
}
