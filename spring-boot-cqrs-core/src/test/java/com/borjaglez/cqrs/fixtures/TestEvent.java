package com.borjaglez.cqrs.fixtures;

import com.borjaglez.cqrs.event.Event;
import com.borjaglez.cqrs.naming.CqrsMessage;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@CqrsMessage(service = "test", module = "order", name = "order_created")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TestEvent extends Event {
  private String data;
}
