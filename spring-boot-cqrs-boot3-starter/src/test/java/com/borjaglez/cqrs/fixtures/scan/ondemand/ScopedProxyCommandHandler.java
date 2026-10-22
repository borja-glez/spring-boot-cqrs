package com.borjaglez.cqrs.fixtures.scan.ondemand;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.fixtures.scan.ScannedCommand;

/** Its definition is a singleton, the factory of a proxy created when it is first used. */
@CommandHandler
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ScopedProxyCommandHandler {

  @HandleCommand
  public String handle(ScannedCommand command) {
    return "scoped-proxy";
  }
}
