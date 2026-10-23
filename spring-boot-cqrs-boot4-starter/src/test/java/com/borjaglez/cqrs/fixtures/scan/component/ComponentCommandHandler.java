package com.borjaglez.cqrs.fixtures.scan.component;

import org.springframework.stereotype.Component;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.fixtures.scan.ScannedCommand;

/** A handler that is also a component, registered by the application's own component scan. */
@CommandHandler
@Component
public class ComponentCommandHandler {

  @HandleCommand
  public String handle(ScannedCommand command) {
    return "component";
  }
}
