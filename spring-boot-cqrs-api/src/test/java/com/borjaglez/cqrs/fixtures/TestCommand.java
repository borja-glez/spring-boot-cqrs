package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@CqrsMessage(service = "test", module = "order", name = "create_order")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TestCommand extends Command {
  private String data;
}
