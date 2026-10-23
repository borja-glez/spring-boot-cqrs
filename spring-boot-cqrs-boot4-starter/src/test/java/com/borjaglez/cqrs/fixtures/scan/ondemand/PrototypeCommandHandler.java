package com.borjaglez.cqrs.fixtures.scan.ondemand;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.fixtures.scan.ScannedCommand;

@CommandHandler
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PrototypeCommandHandler {

  @HandleCommand
  public String handle(ScannedCommand command) {
    return "prototype";
  }
}
