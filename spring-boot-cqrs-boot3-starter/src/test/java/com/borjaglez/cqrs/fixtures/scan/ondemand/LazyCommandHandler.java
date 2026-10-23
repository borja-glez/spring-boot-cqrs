package com.borjaglez.cqrs.fixtures.scan.ondemand;

import org.springframework.context.annotation.Lazy;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.fixtures.scan.ScannedCommand;

@CommandHandler
@Lazy
public class LazyCommandHandler {

  @HandleCommand
  public String handle(ScannedCommand command) {
    return "lazy";
  }
}
