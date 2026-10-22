package com.borjaglez.cqrs.test.architecture.fixtures.good;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

@CqrsMessage(service = "shop", module = "orders", name = "GoodCommand")
public class GoodCommand extends Command {
  public GoodCommand() {}
}
