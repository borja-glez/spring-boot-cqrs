package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.event.Event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Not annotated with {@code @CqrsMessage}, so it is not exposed over RabbitMQ by default. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LocalEvent extends Event {

  private String data;
}
