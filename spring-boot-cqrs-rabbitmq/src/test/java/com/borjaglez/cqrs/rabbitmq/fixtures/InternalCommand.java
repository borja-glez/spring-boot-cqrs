package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.command.Command;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Annotated with {@code @CqrsMessage}, but its handler is marked {@code remote = false}. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "test", module = "order", name = "internal")
public class InternalCommand extends Command {

  private String data;
}
