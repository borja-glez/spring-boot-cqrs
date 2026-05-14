package com.borjaglez.cqrs.example.middleware.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;
import com.borjaglez.cqrs.context.MessageContext;

@CommandHandler
public class GreetCommandHandler {

  private static final Logger log = LoggerFactory.getLogger(GreetCommandHandler.class);

  @HandleCommand
  public String handle(GreetCommand command) {
    MessageContext ctx = MessageContext.current();
    String tenantId = ctx.get("tenantId").orElse("-");
    log.info(
        "Handling greet command [correlationId={}, tenantId={}]", ctx.correlationId(), tenantId);
    return "Hello, " + command.getName() + "! (tenant=" + tenantId + ")";
  }
}
