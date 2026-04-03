package com.borjaglez.cqrs.rabbitmq.fixtures;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@CqrsMessage(service = "test", module = "order", name = "created")
public class TestEvent extends Event {

  private String data;
}
