package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.command.annotation.CommandHandler;
import com.borjaglez.cqrs.command.annotation.HandleCommand;

@CommandHandler
public class TestOrderResultCommandHandler {

  /** A {@code TestResult<TestOrder>}, or the bare {@code TestOrder} for the order "single". */
  @HandleCommand
  public Object handle(TestOrderResultCommand command) {
    TestOrder order = new TestOrder(command.getOrderId());
    return "single".equals(command.getOrderId()) ? order : new TestResult<>(order);
  }
}
