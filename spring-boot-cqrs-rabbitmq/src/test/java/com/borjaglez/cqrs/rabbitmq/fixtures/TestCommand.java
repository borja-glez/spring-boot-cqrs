package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "test", module = "order", name = "create")
public class TestCommand extends Command {

  private String data;
}
