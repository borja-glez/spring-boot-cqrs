package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.command.Command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Not annotated with {@code @CqrsMessage}, so it is not exposed over RabbitMQ by default. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LocalCommand extends Command {

  private String data;
}
